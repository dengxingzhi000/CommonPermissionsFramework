package com.frog.system.notification.metrics;

import com.frog.system.notification.model.NotificationChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetricsRecorder {
    private final MeterRegistry meterRegistry;

    public NotificationMetricsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable();
    }

    public MetricsContext start(NotificationChannel channel) {
        if (meterRegistry == null) {
            return MetricsContext.disabled(channel);
        }
        return MetricsContext.enabled(channel, Timer.start(meterRegistry));
    }

    public void recordAttempt(NotificationChannel channel) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("notification.send.attempts", "channel", channel.name())
                .increment();
    }

    public void recordRetry(NotificationChannel channel) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("notification.send.retries", "channel", channel.name())
                .increment();
    }

    public void recordSkipped(NotificationChannel channel) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("notification.send.skipped", "channel", channel.name())
                .increment();
    }

    public void recordOutcome(MetricsContext context, boolean success) {
        if (context.disabled() || meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                success ? "notification.send.success" : "notification.send.failure",
                "channel", context.channel().name())
                .increment();
        context.sample().stop(Timer.builder("notification.send.latency")
                .tag("channel", context.channel().name())
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry));
    }

    public record MetricsContext(NotificationChannel channel, Timer.Sample sample, boolean disabled) {
        private static MetricsContext disabled(NotificationChannel channel) {
            return new MetricsContext(channel, null, true);
        }

        private static MetricsContext enabled(NotificationChannel channel, Timer.Sample sample) {
            return new MetricsContext(channel, sample, false);
        }
    }
}

