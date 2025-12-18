package com.frog.common.integration.sync.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frog.common.integration.sync.consumer.IdempotentChecker;
import com.frog.common.integration.sync.consumer.KafkaDataSyncConsumer;
import com.frog.common.integration.sync.consumer.RetryableEventProcessor;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import com.frog.common.integration.sync.publisher.DataSyncPublisher;
import com.frog.common.integration.sync.publisher.KafkaDataSyncPublisher;
import com.frog.common.integration.sync.reconciliation.DataReconciliationTask;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据同步自动配置
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(DataSyncProperties.class)
@ConditionalOnProperty(
        prefix = "datasync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DataSyncAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSyncPublisher.class)
    @ConditionalOnBean(KafkaTemplate.class)
    public DataSyncPublisher dataSyncPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            DataSyncProperties properties,
            MeterRegistry meterRegistry,
            Tracer tracer) {

        log.info("[DataSync] Initializing Kafka publisher with topic prefix: {}",
                properties.getTopicPrefix());
        return new KafkaDataSyncPublisher(kafkaTemplate, objectMapper, properties,
                meterRegistry, tracer);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public IdempotentChecker idempotentChecker(
            StringRedisTemplate redisTemplate,
            DataSyncProperties properties) {

        log.info("[DataSync] Initializing idempotent checker");
        return new IdempotentChecker(redisTemplate, properties.getIdempotent());
    }

    @Bean
    @ConditionalOnMissingBean(RetryableEventProcessor.class)
    public RetryableEventProcessor retryableEventProcessor(
            IdempotentChecker idempotentChecker,
            DataSyncPublisher publisher,
            DataSyncProperties properties,
            MeterRegistry meterRegistry,
            ObjectProvider<List<DataSyncHandler>> handlersProvider) {

        RetryableEventProcessor processor = new RetryableEventProcessor(
                idempotentChecker, publisher, properties, meterRegistry);

        // 自动注册所有 DataSyncHandler
        List<DataSyncHandler> handlers = handlersProvider.getIfAvailable();
        if (handlers != null) {
            handlers.forEach(processor::registerHandler);
            log.info("[DataSync] Registered {} handlers", handlers.size());
        }

        return processor;
    }

    @Bean
    @ConditionalOnMissingBean(KafkaDataSyncConsumer.class)
    @ConditionalOnClass(name = "org.springframework.kafka.annotation.KafkaListener")
    public KafkaDataSyncConsumer kafkaDataSyncConsumer(
            RetryableEventProcessor processor,
            ObjectMapper objectMapper,
            Tracer tracer) {

        log.info("[DataSync] Initializing Kafka consumer");
        return new KafkaDataSyncConsumer(processor, objectMapper, tracer);
    }

    @Bean("dataSyncKafkaListenerContainerFactory")
    @ConditionalOnBean(ConsumerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, String> dataSyncKafkaListenerContainerFactory(
            DataSyncProperties properties,
            KafkaProperties kafkaProperties) {

        Map<String, Object> consumerProps = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getConsumer().getConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setBatchListener(false);

        log.info("[DataSync] Configured Kafka listener container factory with concurrency: {}",
                properties.getConsumer().getConcurrency());

        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "datasync.reconciliation", name = "enabled", havingValue = "true")
    public DataReconciliationTask dataReconciliationTask(
            DataSyncProperties properties,
            ObjectProvider<List<DataSyncHandler>> handlersProvider,
            MeterRegistry meterRegistry) {

        log.info("[DataSync] Initializing reconciliation task with cron: {}",
                properties.getReconciliation().getCron());
        return new DataReconciliationTask(properties, handlersProvider.getIfAvailable(), meterRegistry);
    }
}
