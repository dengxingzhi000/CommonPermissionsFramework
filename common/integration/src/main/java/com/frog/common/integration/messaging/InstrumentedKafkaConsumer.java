package com.frog.common.integration.messaging;

import com.frog.common.integration.model.MessageEnvelope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;

@RequiredArgsConstructor
@SuppressWarnings("ClassCanBeRecord")
public class InstrumentedKafkaConsumer {
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public <T> void consume(String handlerName, MessageEnvelope<T> envelope, Consumer<T> handler) {
        Observation observation = Observation.start("messaging.kafka.consume", observationRegistry)
                .lowCardinalityKeyValue("handler", handlerName)
                .lowCardinalityKeyValue("type", envelope.getType() == null ? "unknown" : envelope.getType());
        Span span = tracer.spanBuilder("consume " + handlerName).startSpan();
        span.setAttribute("envelope.id", envelope.getId() == null ? "" : envelope.getId());
        if (envelope.getTraceId() != null) {
            span.setAttribute("trace.envelope", envelope.getTraceId());
        }
        try (Observation.Scope observationScope = observation.openScope(); Scope spanScope = span.makeCurrent()) {
            handler.accept(envelope.getData());
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            observation.error(e);
            throw e;
        } finally {
            span.end();
            observation.stop();
        }
    }
}
