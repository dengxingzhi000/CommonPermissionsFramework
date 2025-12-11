package com.frog.common.integration.messaging;

import com.frog.common.integration.idempotency.IdempotencyChecker;
import com.frog.common.integration.model.MessageEnvelope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Wraps consumer handling with tracing, metrics, and idempotency guard.
 */
@Slf4j
@RequiredArgsConstructor
public class InstrumentedMessageConsumer {
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;
    private final IdempotencyChecker idempotencyChecker;

    public <T> void consume(String handlerName, MessageEnvelope<T> envelope, Consumer<T> handler) {
        String operation = "consume " + handlerName;
        Observation observation = Observation.start("messaging.consume", observationRegistry)
                .lowCardinalityKeyValue("handler", handlerName)
                .lowCardinalityKeyValue("type", envelope.getType() == null ? "unknown" : envelope.getType());
        Span span = tracer.spanBuilder(operation).startSpan();
        span.setAttribute("envelope.id", envelope.getId() == null ? "" : envelope.getId());
        if (envelope.getTraceId() != null) {
            span.setAttribute("trace.envelope", envelope.getTraceId());
        }

        try (Observation.Scope observationScope = observation.openScope(); Scope scope = span.makeCurrent()) {
            if (envelope.getId() != null && !idempotencyChecker.tryAcquire(envelope.getId())) {
                log.info("Skip duplicate message id={}", envelope.getId());
                span.setStatus(StatusCode.OK, "duplicate");
                return;
            }
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
            if (envelope.getId() != null) {
                idempotencyChecker.release(envelope.getId());
            }
        }
    }
}
