package com.frog.common.data.rw.config;

import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAssistConfiguration;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.frog.common.data.rw.aop.ReadWriteRoutingAspect;
import com.frog.common.data.rw.circuitbreaker.SlaveCircuitBreaker;
import com.frog.common.data.rw.dynamic.DynamicDataSourceRefresher;
import com.frog.common.data.rw.endpoint.ReadWriteEndpoint;
import com.frog.common.data.rw.health.ReadWriteHealthIndicator;
import com.frog.common.data.rw.health.SlaveHealthChecker;
import com.frog.common.data.rw.loadbalance.*;
import com.frog.common.data.rw.metrics.ConnectionPoolMetrics;
import com.frog.common.data.rw.routing.ReadWriteRoutingDataSource;
import com.frog.common.data.rw.sql.SqlRoutingInterceptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读写分离自动配置
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@AutoConfiguration(before = {
        DynamicDataSourceAssistConfiguration.class,
        DynamicDataSourceAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@EnableConfigurationProperties(ReadWriteProperties.class)
@ConditionalOnProperty(
        prefix = "spring.datasource.rw",
        name = "enabled",
        havingValue = "true"
)
@EnableScheduling
public class ReadWriteAutoConfiguration {
    private final ReadWriteProperties properties;
    private final MeterRegistry meterRegistry;
    private final Environment environment;

    /**
     * 所有创建的数据源，用于关闭时清理
     */
    private final List<HikariDataSource> allDataSources = new ArrayList<>();

    /**
     * 路由数据源映射
     */
    private final Map<String, ReadWriteRoutingDataSource> routingDataSources = new ConcurrentHashMap<>();

    /**
     * 从库数据源映射
     */
    private final Map<String, Map<String, DataSource>> slaveDataSourcesMap = new ConcurrentHashMap<>();

    public ReadWriteAutoConfiguration(ReadWriteProperties properties,
                                       ObjectProvider<MeterRegistry> meterRegistryProvider,
                                       Environment environment) {
        this.properties = properties;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        this.environment = environment;

        log.info("[RW-Config] Initializing read-write separation with {} group(s)",
                properties.getGroups().size());

        initializeDataSources();
    }

    private void initializeDataSources() {
        for (Map.Entry<String, ReadWriteProperties.DataSourceGroup> entry :
                properties.getGroups().entrySet()) {

            String groupName = entry.getKey();
            ReadWriteProperties.DataSourceGroup group = entry.getValue();

            log.info("[RW-Config] Configuring group [{}] with {} slave(s)",
                    groupName, group.getSlaves().size());

            // 创建主库数据源
            HikariDataSource masterDataSource = createDataSource(
                    groupName + "-master", group.getMaster());
            allDataSources.add(masterDataSource);

            // 创建从库数据源
            Map<String, DataSource> slaveDataSources = new HashMap<>();
            List<SlaveLoadBalancer.SlaveInfo> slaveInfos = new ArrayList<>();

            for (ReadWriteProperties.SlaveDataSourceConfig slaveConfig : group.getSlaves()) {
                String slaveName = slaveConfig.getName();
                HikariDataSource slaveDataSource = createDataSource(
                        groupName + "-" + slaveName, slaveConfig);
                allDataSources.add(slaveDataSource);
                slaveDataSources.put(slaveName, slaveDataSource);

                slaveInfos.add(new SlaveLoadBalancer.SlaveInfo(
                        slaveName,
                        slaveConfig.getWeight(),
                        0,
                        slaveConfig.isAvailable()
                ));
            }

            slaveDataSourcesMap.put(groupName, slaveDataSources);

            // 创建负载均衡器
            SlaveLoadBalancer loadBalancer = createLoadBalancer(
                    group.getLoadBalance() != null ? group.getLoadBalance() : properties.getLoadBalance());

            // 创建路由数据源
            ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource(
                    groupName,
                    masterDataSource,
                    slaveDataSources,
                    properties,
                    loadBalancer,
                    meterRegistry
            );
            routingDataSource.setSlaveInfos(slaveInfos);
            routingDataSource.afterPropertiesSet();

            routingDataSources.put(groupName, routingDataSource);

            log.info("[RW-Config] Group [{}] configured successfully. Master: {}, Slaves: {}",
                    groupName,
                    maskUrl(group.getMaster().getUrl()),
                    slaveDataSources.keySet());
        }
    }

    private HikariDataSource createDataSource(String poolName,
                                               ReadWriteProperties.DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(poolName);
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());

        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout().toMillis());
        hikariConfig.setIdleTimeout(config.getIdleTimeout().toMillis());
        hikariConfig.setMaxLifetime(config.getMaxLifetime().toMillis());

        // 连接测试
        hikariConfig.setConnectionTestQuery("SELECT 1");

        return new HikariDataSource(hikariConfig);
    }

    private SlaveLoadBalancer createLoadBalancer(ReadWriteProperties.LoadBalanceType type) {
        return switch (type) {
            case ROUND_ROBIN -> new RoundRobinLoadBalancer();
            case WEIGHTED_ROUND_ROBIN -> new WeightedRoundRobinLoadBalancer();
            case RANDOM -> new RandomLoadBalancer();
            case WEIGHTED_RANDOM -> new WeightedRandomLoadBalancer();
            case LEAST_CONNECTIONS -> new LeastConnectionsLoadBalancer();
        };
    }

    private String maskUrl(String url) {
        if (url == null) {
            return "null";
        }
        // 掩码敏感信息
        return url.replaceAll("password=[^&]*", "password=***");
    }

    // ==================== Bean Definitions ====================

    /**
     * 默认数据源（第一个组的路由数据源）
     */
    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(
            prefix = "spring.datasource.dynamic",
            name = "enabled",
            havingValue = "false"
    )
    public DataSource dataSource() {
        if (routingDataSources.isEmpty()) {
            throw new IllegalStateException(
                    "[RW-Config] No datasource group configured. " +
                            "Please configure spring.datasource.rw.groups");
        }

        // 返回第一个组作为默认数据源
        String defaultGroup = routingDataSources.keySet().iterator().next();
        log.info("[RW-Config] Using group [{}] as default DataSource", defaultGroup);
        return routingDataSources.get(defaultGroup);
    }

    /**
     * DynamicDatasource integration.
     * <p>
     * When {@code spring.datasource.dynamic} is enabled, expose rw routing datasources to
     * DynamicDatasource via {@link DynamicDataSourceProvider} so {@code @DS("user")} etc can
     * keep working while each group internally does master/slave routing.
     */
    @Bean
    @ConditionalOnMissingBean(DynamicDataSourceProvider.class)
    @ConditionalOnClass(name = "com.baomidou.dynamic.datasource.DynamicRoutingDataSource")
    @ConditionalOnProperty(
            prefix = "spring.datasource.dynamic",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public DynamicDataSourceProvider readWriteDynamicDataSourceProvider() {
        return () -> {
            Map<String, DataSource> dataSources = new HashMap<>(routingDataSources);
            log.info("[RW-Config] Exposed {} rw datasource group(s) to DynamicDatasource: {}",
                    dataSources.size(), dataSources.keySet());
            return dataSources;
        };
    }

    /**
     * 获取指定组的路由数据源
     */
    @Bean
    public ReadWriteDataSourceProvider readWriteDataSourceProvider() {
        return new ReadWriteDataSourceProvider(routingDataSources);
    }

    /**
     * 读写分离路由切面
     */
    @Bean
    @ConditionalOnMissingBean
    public ReadWriteRoutingAspect readWriteRoutingAspect() {
        log.info("[RW-Config] Registering ReadWriteRoutingAspect");
        return new ReadWriteRoutingAspect();
    }

    /**
     * 从库健康检查器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "spring.datasource.rw",
            name = "health-check-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlaveHealthChecker slaveHealthChecker() {
        log.info("[RW-Config] Registering SlaveHealthChecker with interval: {}",
                properties.getHealthCheckInterval());
        return new SlaveHealthChecker(
                routingDataSources,
                slaveDataSourcesMap,
                properties,
                meterRegistry
        );
    }

    /**
     * SQL 路由拦截器（MyBatis 插件）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    public SqlRoutingInterceptor sqlRoutingInterceptor() {
        log.info("[RW-Config] Registering SqlRoutingInterceptor for SQL-based routing");
        return new SqlRoutingInterceptor();
    }

    /**
     * 动态配置刷新器
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamicDataSourceRefresher dynamicDataSourceRefresher() {
        log.info("[RW-Config] Registering DynamicDataSourceRefresher for config refresh");
        return new DynamicDataSourceRefresher(properties, routingDataSources, environment);
    }

    /**
     * 从库熔断器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "com.alibaba.csp.sentinel.SphU")
    public SlaveCircuitBreaker slaveCircuitBreaker() {
        log.info("[RW-Config] Registering SlaveCircuitBreaker with Sentinel integration");
        return new SlaveCircuitBreaker(routingDataSources);
    }

    /**
     * 连接池指标采集器
     */
    @Bean
    @ConditionalOnMissingBean
    public ConnectionPoolMetrics connectionPoolMetrics() {
        ConnectionPoolMetrics metrics = new ConnectionPoolMetrics(meterRegistry);

        // 注册所有数据源的指标
        for (HikariDataSource ds : allDataSources) {
            String poolName = ds.getPoolName();
            String[] parts = poolName.split("-", 2);
            if (parts.length == 2) {
                metrics.registerDataSource(parts[0], parts[1], ds);
            }
        }

        log.info("[RW-Config] Registered ConnectionPoolMetrics for {} datasources",
                allDataSources.size());
        return metrics;
    }

    /**
     * Actuator 健康指示器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public ReadWriteHealthIndicator readWriteHealthIndicator(SlaveHealthChecker healthChecker) {
        log.info("[RW-Config] Registering ReadWriteHealthIndicator for Actuator");
        return new ReadWriteHealthIndicator(healthChecker, readWriteDataSourceProvider());
    }

    /**
     * Actuator 管理端点
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public ReadWriteEndpoint readWriteEndpoint(SlaveHealthChecker healthChecker) {
        log.info("[RW-Config] Registering ReadWriteEndpoint for Actuator");
        return new ReadWriteEndpoint(readWriteDataSourceProvider(), healthChecker);
    }

    @PreDestroy
    public void destroy() {
        log.info("[RW-Config] Shutting down all datasources...");
        for (HikariDataSource dataSource : allDataSources) {
            try {
                if (!dataSource.isClosed()) {
                    dataSource.close();
                    log.debug("[RW-Config] Closed datasource: {}", dataSource.getPoolName());
                }
            } catch (Exception e) {
                log.warn("[RW-Config] Error closing datasource {}: {}",
                        dataSource.getPoolName(), e.getMessage());
            }
        }
    }

    /**
     * 数据源提供器
     * <p>
     * 用于获取指定组的路由数据源
     */
    public record ReadWriteDataSourceProvider(
            Map<String, ReadWriteRoutingDataSource> routingDataSources
    ) {
        /**
         * 获取指定组的路由数据源
         */
        public ReadWriteRoutingDataSource getDataSource(String groupName) {
            ReadWriteRoutingDataSource ds = routingDataSources.get(groupName);
            if (ds == null) {
                throw new IllegalArgumentException(
                        "No datasource group found: " + groupName +
                                ". Available groups: " + routingDataSources.keySet());
            }
            return ds;
        }

        /**
         * 获取所有组名
         */
        public java.util.Set<String> getGroupNames() {
            return routingDataSources.keySet();
        }
    }
}
