package com.frog.common.integration.messaging;

import com.frog.common.integration.config.IntegrationProperties;
import com.frog.common.integration.model.MessageEnvelope;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public class ReliableMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;
    private final IntegrationProperties properties;

    public <T> void sendSync(String exchange, String routingKey, MessageEnvelope<T> envelope) {
        doSend(exchange, routingKey, envelope, true, 0);
    }

    public <T> void sendAsync(String exchange, String routingKey, MessageEnvelope<T> envelope) {
        doSend(exchange, routingKey, envelope, false, 0);
    }

    public <T> void sendDelayed(String exchange, String routingKey, MessageEnvelope<T> envelope, int delayMillis) {
        doSend(exchange, routingKey, envelope, false, delayMillis);
    }

    public <T> void sendOrderly(String exchange, String routingKey, MessageEnvelope<T> envelope, String hashKey) {
        int partitions = Math.max(1, properties.getOrderingPartitions());
        String orderedKey = routingKey + "." + Math.floorMod(hashKey.hashCode(), partitions);
        doSend(exchange, orderedKey, envelope, false, 0);
    }

    private <T> void doSend(String exchange, String routingKey, MessageEnvelope<T> envelope, boolean waitForConfirm,
                            int delayMillis) {
        Assert.notNull(envelope, "envelope must not be null");
        Observation observation = Observation.start("messaging.publish", observationRegistry)
                .lowCardinalityKeyValue("exchange", exchange)
                .lowCardinalityKeyValue("routingKey", routingKey)
                .lowCardinalityKeyValue("type", envelope.getType() == null ? "unknown" : envelope.getType());
        Span span = tracer.spanBuilder("publish " + routingKey).startSpan();
        if (envelope.getTraceId() != null) {
            span.setAttribute("trace.envelope", envelope.getTraceId());
        }

        try (Observation.Scope observationScope = observation.openScope(); Scope scope = span.makeCurrent()) {
            CorrelationData correlationData = new CorrelationData(envelope.getId() ==
                    null ? UUID.randomUUID().toString() : envelope.getId());
            rabbitTemplate.convertAndSend(exchange, routingKey, envelope, message ->
                    applyDelay(message, delayMillis), correlationData);
            if (waitForConfirm && properties.isPublisherConfirms()) {
                waitForConfirm(correlationData, properties.getConfirmTimeout());
            }
            observation.stop();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            observation.error(e);
            throw e instanceof AmqpException ? (AmqpException) e : new AmqpException("Send message failed", e);
        } finally {
            span.end();
        }
    }

    private void waitForConfirm(CorrelationData correlationData, Duration timeout)
            throws TimeoutException, ExecutionException, InterruptedException {
        if (correlationData == null) {
            return;
        }
        var confirm = correlationData.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        String cause = confirm.reason();
        if (!confirm.ack()) {
            throw new AmqpException("Message NACK: " + (cause == null || cause.isBlank() ? "unknown reason" : cause));
        }
    }

    private Message applyDelay(Message message, int delayMillis) {
        if (delayMillis > 0 && properties.isDelayedExchangeEnabled()) {
            message.getMessageProperties().setHeader("x-delay", delayMillis);
        }
        if (message.getBody().length == 0) {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setContentEncoding(StandardCharsets.UTF_8.name());
        }
        return message;
    }
}
