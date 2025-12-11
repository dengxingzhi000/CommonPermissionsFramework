package com.frog.system.notification.impl;

import com.frog.system.notification.NotificationService;
import com.frog.system.notification.audit.NotificationAuditService;
import com.frog.system.notification.channel.ChannelNotifier;
import com.frog.system.notification.config.NotificationProperties;
import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import com.frog.system.notification.model.NotificationDeliveryStatus;
import com.frog.system.notification.metrics.NotificationMetricsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {
    private final Map<NotificationChannel, ChannelNotifier> notifierRegistry;
    private final NotificationProperties properties;
    private final NotificationAuditService auditService;
    private final NotificationMetricsRecorder metricsRecorder;

    public NotificationServiceImpl(List<ChannelNotifier> notifiers,
                                   NotificationProperties properties,
                                   NotificationAuditService auditService,
                                   NotificationMetricsRecorder metricsRecorder) {
        this.properties = properties;
        this.auditService = auditService;
        this.metricsRecorder = metricsRecorder;
        this.notifierRegistry = new EnumMap<>(NotificationChannel.class);
        for (ChannelNotifier notifier : notifiers) {
            ChannelNotifier existing = notifierRegistry.put(notifier.channel(), notifier);
            if (existing != null) {
                log.warn("Replacing notifier {} with {} for channel {}",
                        existing.getClass().getSimpleName(),
                        notifier.getClass().getSimpleName(),
                        notifier.channel());
            }
        }
    }

    @Override
    public void send(NotificationCommand command) {
        if (command == null) {
            log.debug("Skip notification: command is null");
            return;
        }
        if (command.getChannels() == null || command.getChannels().isEmpty()) {
            log.debug("Skip notification: no channels defined. refId={}", command.getReferenceId());
            return;
        }

        command.getChannels().forEach(channel -> deliver(channel, command));
    }

    private void deliver(NotificationChannel channel, NotificationCommand command) {
        ChannelNotifier notifier = notifierRegistry.get(channel);
        if (notifier == null) {
            log.warn("No notifier registered for channel {}. refId={}", channel, command.getReferenceId());
            metricsRecorder.recordSkipped(channel);
            auditService.record(command, channel, NotificationDeliveryStatus.SKIPPED,
                    "Channel not configured");
            return;
        }

        DeliveryOutcome outcome = runWithRetry(channel, command, notifier::send);
        if (outcome.success()) {
            auditService.record(command, channel, NotificationDeliveryStatus.SUCCESS, null);
        } else {
            String errorMessage = outcome.error() != null ? outcome.error().getMessage() : "Unknown error";
            auditService.record(command, channel, NotificationDeliveryStatus.FAILED, errorMessage);
        }
    }

    private DeliveryOutcome runWithRetry(NotificationChannel channel,
                                         NotificationCommand command,
                                         Consumer<NotificationCommand> consumer) {
        NotificationProperties.Retry retry = properties.getRetry();
        int maxAttempts = Math.max(1, retry.getMaxAttempts());
        List<Long> backoff = retry.getBackoff();
        NotificationMetricsRecorder.MetricsContext metricsContext = metricsRecorder.start(channel);
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            metricsRecorder.recordAttempt(channel);
            try {
                consumer.accept(command);
                metricsRecorder.recordOutcome(metricsContext, true);
                return DeliveryOutcome.succeeded();
            } catch (Exception ex) {
                lastError = ex;
                if (attempt == maxAttempts) {
                    log.error("Channel {} failed after {} attempts. refId={}",
                            channel, maxAttempts, command.getReferenceId(), ex);
                    metricsRecorder.recordOutcome(metricsContext, false);
                    return DeliveryOutcome.failed(ex);
                }
                long sleepMs = resolveBackoff(backoff, attempt - 1);
                log.warn("Channel {} delivery failed (attempt {}). refId={}, retry in {}ms",
                        channel, attempt, command.getReferenceId(), sleepMs, ex);
                metricsRecorder.recordRetry(channel);
                sleep(sleepMs);
            }
        }
        metricsRecorder.recordOutcome(metricsContext, false);
        return DeliveryOutcome.failed(lastError);
    }

    private long resolveBackoff(List<Long> backoff, int index) {
        if (backoff == null || backoff.isEmpty()) {
            return 0L;
        }
        int safeIndex = Math.min(index, backoff.size() - 1);
        return backoff.get(safeIndex);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record DeliveryOutcome(boolean success, Exception error) {
        private static DeliveryOutcome succeeded() {
            return new DeliveryOutcome(true, null);
        }

        private static DeliveryOutcome failed(Exception error) {
            return new DeliveryOutcome(false, error);
        }
    }
}
