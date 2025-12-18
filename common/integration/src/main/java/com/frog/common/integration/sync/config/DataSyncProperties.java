package com.frog.common.integration.sync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据同步配置属性
 *
 * @author Deng
 * @since 2025-12-16
 */
@Data
@ConfigurationProperties(prefix = "datasync")
public class DataSyncProperties {
    /**
     * 是否启用数据同步
     */
    private boolean enabled = true;

    /**
     * 当前服务名称
     */
    private String serviceName = "unknown";

    /**
     * Kafka 主题前缀
     */
    private String topicPrefix = "datasync";

    /**
     * 死信队列主题
     */
    private String deadLetterTopic = "datasync.dlq";

    /**
     * 发布超时时间（毫秒）
     */
    private long publishTimeoutMs = 5000;

    /**
     * 消费者配置
     */
    private ConsumerConfig consumer = new ConsumerConfig();

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 幂等配置
     */
    private IdempotentConfig idempotent = new IdempotentConfig();

    /**
     * 对账配置
     */
    private ReconciliationConfig reconciliation = new ReconciliationConfig();

    /**
     * 处理器映射：aggregateType -> handlerBeanName
     */
    private Map<String, String> handlers = new HashMap<>();

    @Data
    public static class ConsumerConfig {
        /**
         * 消费者组 ID
         */
        private String groupId = "datasync-consumer";

        /**
         * 并发消费者数量
         */
        private int concurrency = 3;

        /**
         * 批量拉取大小
         */
        private int batchSize = 100;

        /**
         * 消费超时时间
         */
        private Duration pollTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class RetryConfig {
        /**
         * 是否启用重试
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;

        /**
         * 初始退避间隔（毫秒）
         */
        private long initialIntervalMs = 1000;

        /**
         * 最大退避间隔（毫秒）
         */
        private long maxIntervalMs = 30000;

        /**
         * 退避乘数
         */
        private double multiplier = 2.0;
    }

    @Data
    public static class IdempotentConfig {
        /**
         * 是否启用幂等
         */
        private boolean enabled = true;

        /**
         * 幂等 key 过期时间（秒）
         */
        private long expireSeconds = 86400; // 24 hours

        /**
         * Redis key 前缀
         */
        private String keyPrefix = "datasync:idempotent:";
    }

    @Data
    public static class ReconciliationConfig {
        /**
         * 是否启用对账
         */
        private boolean enabled = false;

        /**
         * 对账 cron 表达式
         */
        private String cron = "0 0 3 * * ?"; // 每天凌晨 3 点

        /**
         * 对账批次大小
         */
        private int batchSize = 1000;

        /**
         * 是否自动修复
         */
        private boolean autoFix = false;
    }
}
