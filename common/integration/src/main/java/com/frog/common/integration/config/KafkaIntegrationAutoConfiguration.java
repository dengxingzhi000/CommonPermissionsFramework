package com.frog.common.integration.config;

import com.frog.common.integration.messaging.InstrumentedKafkaConsumer;
import com.frog.common.integration.messaging.KafkaMessagePublisher;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@AutoConfiguration
@EnableKafka
@EnableConfigurationProperties({KafkaIntegrationProperties.class})
@RequiredArgsConstructor
public class KafkaIntegrationAutoConfiguration {
    private final KafkaIntegrationProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, Object> kafkaProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, properties.getClientId());
        config.put(ProducerConfig.ACKS_CONFIG, properties.getAcks());
        config.put(ProducerConfig.RETRIES_CONFIG, properties.getRetries());
        config.put(ProducerConfig.LINGER_MS_CONFIG, properties.getLingerMs());
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, properties.getBatchSize());
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, properties.getMaxInFlight());
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, properties.isIdempotence());
        
        JacksonJsonSerializer<Object> jsonSerializer = new JacksonJsonSerializer<>();
        jsonSerializer.setAddTypeInfo(false);
        
        return new DefaultKafkaProducerFactory<>(config, new StringSerializer(), jsonSerializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsumerFactory<String, Object> kafkaConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getBootstrapServers());
        
        JacksonJsonDeserializer<Object> jsonDeserializer = new JacksonJsonDeserializer<>();
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setUseTypeHeaders(false);
        
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), jsonDeserializer);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaMessagePublisher kafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                                       ObservationRegistry observationRegistry,
                                                       Tracer tracer) {
        return new KafkaMessagePublisher(kafkaTemplate, observationRegistry, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public InstrumentedKafkaConsumer instrumentedKafkaConsumer(ObservationRegistry observationRegistry,
                                                               Tracer tracer) {
        return new InstrumentedKafkaConsumer(observationRegistry, tracer);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        if (!properties.isDlqEnabled()) {
            return new DefaultErrorHandler();
        }
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + properties.getDlqSuffix(), record.partition()));
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(properties.getBackoffInitial().toMillis());
        backOff.setMultiplier(properties.getBackoffMultiplier());
        backOff.setMaxInterval(properties.getBackoffMax().toMillis());
        backOff.setMaxElapsedTime(properties.getBackoffMax().toMillis() * maxAttempts);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
