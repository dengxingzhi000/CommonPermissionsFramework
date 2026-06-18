package com.frog.common.metrics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义指标
 *
 * @author Deng
 * createData 2025/10/22 14:01
 * @version 1.1 - 使用Guava有界缓存替代无界ConcurrentHashMap防止内存泄漏
 */
@Component
@RequiredArgsConstructor
public class BusinessMetrics {
    private final MeterRegistry registry;

    private final Cache<String, Counter> counterCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    private final Cache<String, Timer> timerCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    private final Cache<String, DistributionSummary> summaryCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    private final Cache<String, AtomicLong> gaugeCache = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    /**
     * 记录业务指标 - 计数器
     * 示例：登录次数、订单数量、支付次数
     */
    public void recordCount(String metricName, String... tags) {
        String key = metricName + String.join(",", tags);
        try {
            Counter counter = counterCache.get(key, () ->
                    Counter.builder(metricName)
                            .tags(tags)
                            .description("业务计数指标: " + metricName)
                            .register(registry)
            );
            if (counter != null) counter.increment();
        } catch (Exception e) {
            // Metrics should never throw
        }
    }

    /**
     * 记录业务指标 - 计时器
     * 示例：接口耗时、业务处理时长
     */
    public void recordTime(String metricName, long timeMs, String... tags) {
        String key = metricName + String.join(",", tags);
        try {
            Timer timer = timerCache.get(key, () ->
                    Timer.builder(metricName)
                            .tags(tags)
                            .description("业务耗时指标: " + metricName)
                            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                            .publishPercentileHistogram()
                            .register(registry)
            );
            if (timer != null) timer.record(timeMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // Metrics should never throw
        }
    }

    /**
     * 记录业务指标 - 分布摘要
     * 示例：订单金额分布、请求体大小
     */
    public void recordDistribution(String metricName, double value, String... tags) {
        String key = metricName + String.join(",", tags);
        try {
            DistributionSummary summary = summaryCache.get(key, () ->
                    DistributionSummary.builder(metricName)
                            .tags(tags)
                            .description("业务分布指标: " + metricName)
                            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                            .register(registry)
            );
            if (summary != null) summary.record(value);
        } catch (Exception e) {
            // Metrics should never throw
        }
    }

    /**
     * 记录业务指标 - 仪表盘
     * 示例：在线用户数、队列长度、缓存命中率
     */
    public void recordGauge(String metricName, long value, String... tags) {
        String key = metricName + String.join(",", tags);
        try {
            AtomicLong atomicValue = gaugeCache.get(key, () -> {
                AtomicLong atomic = new AtomicLong(value);
                Gauge.builder(metricName, atomic, AtomicLong::get)
                        .tags(tags)
                        .description("业务状态指标: " + metricName)
                        .register(registry);
                return atomic;
            });
            if (atomicValue != null) atomicValue.set(value);
        } catch (Exception e) {
            // Metrics should never throw
        }
    }

    /**
     * 快捷方法 - 记录登录
     */
    public void recordLogin(boolean success, String source) {
        recordCount("business.login.total",
                "success", String.valueOf(success),
                "source", source);
    }

    /**
     * 快捷方法 - 记录API调用
     */
    public void recordApi(String api, long timeMs, boolean success) {
        recordCount("business.api.calls",
                "api", api,
                "success", String.valueOf(success));
        recordTime("business.api.duration", timeMs,
                "api", api);
    }

    /**
     * 快捷方法 - 记录缓存命中
     */
    public void recordCache(String cacheName, boolean hit) {
        recordCount("business.cache.access",
                "cache", cacheName,
                "hit", String.valueOf(hit));
    }

    /**
     * 登录成功率
     */
    public void recordLoginAttempt(boolean success, String reason) {
        Counter.builder("business.login.attempts")
                .tag("success", String.valueOf(success))
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * 权限授予审计
     */
    public void recordPermissionGrant(String type, int count) {
        Counter.builder("business.permission.grants")
                .tag("type", type)
                .register(registry)
                .increment(count);
    }

    /**
     * 临时权限过期预警
     */
    public void recordExpiringPermissions(int count) {
        Gauge.builder("business.permissions.expiring", () -> count)
                .description("即将过期的临时权限数量")
                .register(registry);
    }
}
