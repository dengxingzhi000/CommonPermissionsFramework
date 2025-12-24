package com.frog.common.integration.config;

import com.frog.common.integration.idempotency.IdempotencyChecker;
import com.frog.common.integration.idempotency.MemoryIdempotencyChecker;
import com.frog.common.integration.messaging.InstrumentedMessageConsumer;
import com.frog.common.integration.messaging.ReliableMessagePublisher;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.UUID;

@AutoConfiguration
@EnableConfigurationProperties(IntegrationProperties.class)
@RequiredArgsConstructor
@Slf4j
public class RabbitIntegrationAutoConfiguration {
    private final IntegrationProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter messageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setCreateMessageIds(true);
        converter.setBeanClassLoader(this.getClass().getClassLoader());
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    public RabbitTemplate rabbitTemplate(CachingConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        if (properties.isPublisherConfirms()) {
            connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            connectionFactory.setPublisherReturns(true);
        }
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setMessageConverter(messageConverter);
        template.setBeforePublishPostProcessors(message -> {
            // Ensure persistent delivery by default
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            // Set a correlation id if missing to map confirms
            if (message.getMessageProperties().getCorrelationId() == null) {
                message.getMessageProperties().setCorrelationId(UUID.randomUUID().toString());
            }
            return message;
        });
        template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            String correlationId = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                return;
            }
            // NACK logging; publisher will throw if waiting synchronously.
            log.error("RabbitMQ publish NACK id={} cause={}", correlationId, cause);
        });
        template.setReturnsCallback(returned ->
                log.error("RabbitMQ message returned exchange={} routingKey={} replyCode={} replyText={}",
                        returned.getExchange(),
                        returned.getRoutingKey(),
                        returned.getReplyCode(),
                        returned.getReplyText()));
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public ReliableMessagePublisher reliableMessagePublisher(RabbitTemplate rabbitTemplate,
                                                             ObservationRegistry observationRegistry,
                                                             Tracer tracer) {
        return new ReliableMessagePublisher(rabbitTemplate, observationRegistry, tracer, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public InstrumentedMessageConsumer instrumentedMessageConsumer(
            ObservationRegistry observationRegistry,
            Tracer tracer,
            IdempotencyChecker idempotencyChecker) {
        return new InstrumentedMessageConsumer(observationRegistry, tracer, idempotencyChecker);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyChecker idempotencyChecker() {
        // Default in-memory implementation; override with Redis/DB in production.
        return new MemoryIdempotencyChecker(Duration.ofHours(1));
    }
}
