package com.frog.common.integration.sync.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frog.common.integration.sync.config.DataSyncProperties;
import com.frog.common.integration.sync.event.DataSyncEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 数据同步事件发布器
 * <p>
 * 特性：
 * - 分布式追踪集成（OpenTelemetry）
 * - Prometheus 指标监控
 * - 死信队列支持
 * - 批量发布优化
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class KafkaDataSyncPublisher implements DataSyncPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DataSyncProperties properties;
    private final Tracer tracer;

    // Metrics
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;
    private final Counter deadLetterCounter;
    private final Timer publishTimer;

    public KafkaDataSyncPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   DataSyncProperties properties,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tracer = tracer;

        // Initialize metrics
        this.publishSuccessCounter = Counter.builder("datasync.publish.success")
                .description("Number of successful publish operations")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("datasync.publish.failure")
                .description("Number of failed publish operations")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("datasync.deadletter.count")
                .description("Number of events sent to dead letter queue")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("datasync.publish.duration")
                .description("Time taken to publish events")
                .register(meterRegistry);
    }

    @Override
    public boolean publish(DataSyncEvent event) {
        Span span = tracer.spanBuilder("datasync.publish")
                .setAttribute("aggregate.type", event.getAggregateType())
                .setAttribute("event.type", event.getEventType().name())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            // Inject trace context
            event.setTraceId(span.getSpanContext().getTraceId());
            event.setSpanId(span.getSpanContext().getSpanId());

            return publishTimer.record(() -> doPublish(event));
        } finally {
            span.end();
        }
    }

    private boolean doPublish(DataSyncEvent event) {
        try {
            String topic = event.getTopicName(properties.getTopicPrefix());
            String key = event.getPartitionKey();
            String payload = objectMapper.writeValueAsString(event);

            SendResult<String, String> result = kafkaTemplate.send(topic, key, payload)
                    .get(properties.getPublishTimeoutMs(), TimeUnit.MILLISECONDS);

            log.debug("[DataSync] Published event: topic={}, partition={}, offset={}, eventId={}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getEventId());

            publishSuccessCounter.increment();
            return true;

        } catch (Exception e) {
            log.error("[DataSync] Failed to publish event: eventId={}, error={}",
                    event.getEventId(), e.getMessage(), e);
            publishFailureCounter.increment();
            return false;
        }
    }

    @Override
    public void publishAsync(DataSyncEvent event) {
        Span span = tracer.spanBuilder("datasync.publish.async")
                .setAttribute("aggregate.type", event.getAggregateType())
                .setAttribute("event.type", event.getEventType().name())
                .startSpan();

        // Inject trace context
        event.setTraceId(span.getSpanContext().getTraceId());
        event.setSpanId(span.getSpanContext().getSpanId());

        try {
            String topic = event.getTopicName(properties.getTopicPrefix());
            String key = event.getPartitionKey();
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, key, payload)
                    .thenAccept(result -> {
                        log.debug("[DataSync] Async published: topic={}, eventId={}",
                                topic, event.getEventId());
                        publishSuccessCounter.increment();
                        span.end();
                    })
                    .exceptionally(ex -> {
                        log.error("[DataSync] Async publish failed: eventId={}", event.getEventId(), ex);
                        publishFailureCounter.increment();
                        span.recordException(ex);
                        span.end();
                        return null;
                    });

        } catch (Exception e) {
            span.recordException(e);
            span.end();
            log.error("[DataSync] Failed to initiate async publish: eventId={}", event.getEventId(), e);
        }
    }

    @Override
    public int publishBatch(List<DataSyncEvent> events) {
        int successCount = 0;
        for (DataSyncEvent event : events) {
            if (publish(event)) {
                successCount++;
            }
        }
        return successCount;
    }

    @Override
    public void publishToDeadLetter(DataSyncEvent event, String reason) {
        try {
            event.setLastFailureReason(reason);
            String topic = properties.getDeadLetterTopic();
            String key = event.getPartitionKey();
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(topic, key, payload);

            deadLetterCounter.increment();
            log.warn("[DataSync] Event sent to DLQ: eventId={}, reason={}",
                    event.getEventId(), reason);

        } catch (Exception e) {
            log.error("[DataSync] Failed to send to DLQ: eventId={}", event.getEventId(), e);
        }
    }
}
