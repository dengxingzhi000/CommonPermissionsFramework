package com.frog.common.data.rw.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 连接池指标采集器
 * <p>
 * 参考：
 * - 阿里 TDDL DataSourceMonitor
 * - 美团 Zebra DataSourceMetrics
 * - HikariCP Metrics
 * <p>
 * 采集指标：
 * - 活跃连接数
 * - 空闲连接数
 * - 等待线程数
 * - 连接获取耗时
 * - 连接使用耗时
 * - 连接创建耗时
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class ConnectionPoolMetrics {
    private static final String METRIC_PREFIX = "datasource.rw.pool";

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> connectionAcquireTimers = new ConcurrentHashMap<>();
    private final Map<String, Timer> connectionUseTimers = new ConcurrentHashMap<>();

    public ConnectionPoolMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 注册数据源指标
     */
    public void registerDataSource(String groupName, String dsName, DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikariDs)) {
            log.warn("[Pool-Metrics] DataSource [{}] is not HikariDataSource, skipping metrics",
                    dsName);
            return;
        }

        Tags tags = Tags.of("group", groupName, "name", dsName);
        String fullName = groupName + "." + dsName;

        // 尝试获取 MXBean
        HikariPoolMXBean poolMXBean = hikariDs.getHikariPoolMXBean();
        if (poolMXBean == null) {
            log.warn("[Pool-Metrics] HikariPoolMXBean not available for [{}], metrics will be limited",
                    fullName);
            registerBasicMetrics(hikariDs, tags, fullName);
            return;
        }

        // 活跃连接数
        Gauge.builder(METRIC_PREFIX + ".active", poolMXBean, HikariPoolMXBean::getActiveConnections)
                .tags(tags)
                .description("Number of active connections")
                .register(meterRegistry);

        // 空闲连接数
        Gauge.builder(METRIC_PREFIX + ".idle", poolMXBean, HikariPoolMXBean::getIdleConnections)
                .tags(tags)
                .description("Number of idle connections")
                .register(meterRegistry);

        // 总连接数
        Gauge.builder(METRIC_PREFIX + ".total", poolMXBean, HikariPoolMXBean::getTotalConnections)
                .tags(tags)
                .description("Total number of connections")
                .register(meterRegistry);

        // 等待线程数
        Gauge.builder(METRIC_PREFIX + ".pending", poolMXBean, HikariPoolMXBean::getThreadsAwaitingConnection)
                .tags(tags)
                .description("Number of threads waiting for a connection")
                .register(meterRegistry);

        // 最大连接数
        Gauge.builder(METRIC_PREFIX + ".max", hikariDs, HikariDataSource::getMaximumPoolSize)
                .tags(tags)
                .description("Maximum pool size")
                .register(meterRegistry);

        // 最小空闲连接数
        Gauge.builder(METRIC_PREFIX + ".min", hikariDs, HikariDataSource::getMinimumIdle)
                .tags(tags)
                .description("Minimum idle connections")
                .register(meterRegistry);

        // 连接获取耗时
        Timer acquireTimer = Timer.builder(METRIC_PREFIX + ".acquire")
                .tags(tags)
                .description("Connection acquire time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        connectionAcquireTimers.put(fullName, acquireTimer);

        // 连接使用耗时
        Timer useTimer = Timer.builder(METRIC_PREFIX + ".use")
                .tags(tags)
                .description("Connection use time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        connectionUseTimers.put(fullName, useTimer);

        // 连接池利用率
        Gauge.builder(METRIC_PREFIX + ".utilization", poolMXBean,
                        bean -> {
                            int total = bean.getTotalConnections();
                            if (total == 0) return 0.0;
                            return (double) bean.getActiveConnections() / total;
                        })
                .tags(tags)
                .description("Connection pool utilization (0.0 - 1.0)")
                .register(meterRegistry);

        log.info("[Pool-Metrics] Registered metrics for datasource [{}]", fullName);
    }

    /**
     * 注册基础指标（当 MXBean 不可用时）
     */
    private void registerBasicMetrics(HikariDataSource hikariDs, Tags tags, String fullName) {
        Gauge.builder(METRIC_PREFIX + ".max", hikariDs, HikariDataSource::getMaximumPoolSize)
                .tags(tags)
                .description("Maximum pool size")
                .register(meterRegistry);

        Gauge.builder(METRIC_PREFIX + ".min", hikariDs, HikariDataSource::getMinimumIdle)
                .tags(tags)
                .description("Minimum idle connections")
                .register(meterRegistry);

        log.info("[Pool-Metrics] Registered basic metrics for datasource [{}]", fullName);
    }

    /**
     * 记录连接获取耗时
     */
    public void recordAcquireTime(String groupName, String dsName, long nanos) {
        String fullName = groupName + "." + dsName;
        Timer timer = connectionAcquireTimers.get(fullName);
        if (timer != null) {
            timer.record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 记录连接使用耗时
     */
    public void recordUseTime(String groupName, String dsName, long nanos) {
        String fullName = groupName + "." + dsName;
        Timer timer = connectionUseTimers.get(fullName);
        if (timer != null) {
            timer.record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 获取连接池状态快照
     */
    public Map<String, Object> getPoolSnapshot(String groupName, String dsName,
                                                DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikariDs)) {
            return Map.of("error", "Not a HikariDataSource");
        }

        HikariPoolMXBean poolMXBean = hikariDs.getHikariPoolMXBean();
        if (poolMXBean == null) {
            return Map.of(
                    "maxPoolSize", hikariDs.getMaximumPoolSize(),
                    "minIdle", hikariDs.getMinimumIdle(),
                    "status", "Pool not yet initialized"
            );
        }

        return Map.of(
                "activeConnections", poolMXBean.getActiveConnections(),
                "idleConnections", poolMXBean.getIdleConnections(),
                "totalConnections", poolMXBean.getTotalConnections(),
                "threadsAwaitingConnection", poolMXBean.getThreadsAwaitingConnection(),
                "maxPoolSize", hikariDs.getMaximumPoolSize(),
                "minIdle", hikariDs.getMinimumIdle(),
                "connectionTimeout", hikariDs.getConnectionTimeout(),
                "idleTimeout", hikariDs.getIdleTimeout(),
                "maxLifetime", hikariDs.getMaxLifetime()
        );
    }
}
