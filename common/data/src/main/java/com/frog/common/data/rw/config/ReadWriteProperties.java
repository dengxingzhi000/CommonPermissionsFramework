package com.frog.common.data.rw.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 读写分离配置属性
 *
 * @author Deng
 * @since 2025-12-16
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.rw")
public class ReadWriteProperties {
    /**
     * 是否启用读写分离
     */
    private boolean enabled = false;

    /**
     * 数据源组配置
     * key: 数据源组名称（如 user, permission）
     * value: 主从配置
     */
    private Map<String, DataSourceGroup> groups = new HashMap<>();

    /**
     * 全局负载均衡策略
     */
    private LoadBalanceType loadBalance = LoadBalanceType.ROUND_ROBIN;

    /**
     * 复制延迟容忍时间（超过此时间强制走主库）
     */
    private Duration replicationLagTolerance = Duration.ofSeconds(1);

    /**
     * 写后读主库持续时间（解决读写一致性）
     */
    private Duration readMasterAfterWrite = Duration.ofSeconds(2);

    /**
     * 是否启用健康检查
     */
    private boolean healthCheckEnabled = true;

    /**
     * 健康检查间隔
     */
    private Duration healthCheckInterval = Duration.ofSeconds(30);

    /**
     * 连续失败次数后标记为不可用
     */
    private int failureThreshold = 3;

    /**
     * 数据源组配置
     */
    @Data
    public static class DataSourceGroup {
        /**
         * 主库配置
         */
        private DataSourceConfig master;

        /**
         * 从库配置列表
         */
        private List<SlaveDataSourceConfig> slaves = new ArrayList<>();

        /**
         * 是否启用从库（可临时关闭）
         */
        private boolean slavesEnabled = true;

        /**
         * 负载均衡策略（覆盖全局）
         */
        private LoadBalanceType loadBalance;
    }

    /**
     * 数据源配置
     */
    @Data
    public static class DataSourceConfig {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";

        // HikariCP 配置
        private int minimumIdle = 5;
        private int maximumPoolSize = 20;
        private Duration connectionTimeout = Duration.ofSeconds(30);
        private Duration idleTimeout = Duration.ofMinutes(10);
        private Duration maxLifetime = Duration.ofMinutes(30);
    }

    /**
     * 从库数据源配置（带权重）
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SlaveDataSourceConfig extends DataSourceConfig {
        /**
         * 从库名称（用于日志和监控）
         */
        private String name = "slave";

        /**
         * 权重（用于加权轮询）
         */
        private int weight = 1;

        /**
         * 是否可用
         */
        private boolean available = true;
    }

    /**
     * 负载均衡类型
     */
    public enum LoadBalanceType {
        /**
         * 轮询
         */
        ROUND_ROBIN,

        /**
         * 加权轮询
         */
        WEIGHTED_ROUND_ROBIN,

        /**
         * 随机
         */
        RANDOM,

        /**
         * 加权随机
         */
        WEIGHTED_RANDOM,

        /**
         * 最少连接
         */
        LEAST_CONNECTIONS
    }
}
