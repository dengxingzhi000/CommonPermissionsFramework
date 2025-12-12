package com.frog.common.integration.messaging;

import com.frog.common.integration.model.MessageEnvelope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class KafkaMessagePublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public <T> void send(String topic, String key, MessageEnvelope<T> envelope) {
        Observation observation = Observation.start("messaging.kafka.publish", observationRegistry)
                .lowCardinalityKeyValue("topic", topic)
                .lowCardinalityKeyValue("type", envelope.getType() == null ? "unknown" : envelope.getType());
        Span span = tracer.spanBuilder("kafka publish " + topic).startSpan();
        if (envelope.getTraceId() != null) {
            span.setAttribute("trace.envelope", envelope.getTraceId());
        }
        try (Observation.Scope observationScope = observation.openScope(); Scope spanScope = span.makeCurrent()) {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, envelope);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    span.recordException(ex);
                    span.setStatus(StatusCode.ERROR, ex.getMessage());
                    observation.error(ex);
                } else {
                    span.setStatus(StatusCode.OK);
                    if (result != null) {
                        span.setAttribute("partition", result.getRecordMetadata().partition());
                        span.setAttribute("offset", result.getRecordMetadata().offset());
                    }
                }
                observation.stop();
                span.end();
            });
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            observation.error(e);
            observation.stop();
            span.end();
            throw e;
        }
    }
}
