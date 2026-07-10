package com.frog.common.integration.sync.reconciliation;

import com.frog.common.integration.sync.config.DataSyncProperties;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据对账定时任务
 * <p>
 * 定期检查冗余数据一致性，发现不一致时进行修复
 * 设计参考：
 * - 阿里巴巴：T+1 对账 + 实时告警
 * - 美团：分钟级抽样对账
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class DataReconciliationTask {

    private static final String LOCK_SCRIPT =
            "if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final DataSyncProperties properties;
    private final Map<String, ReconcilableHandler> handlers;
    private final StringRedisTemplate redisTemplate;
    private final String lockValue;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutorService executorService;
    private final List<ReconciliationListener> listeners;

    // Metrics
    private final Counter reconcileSuccessCounter;
    private final Counter reconcileFailureCounter;
    private final Counter reconcileFixCounter;

    public DataReconciliationTask(DataSyncProperties properties,
                                   List<DataSyncHandler> handlerList,
                                   StringRedisTemplate redisTemplate,
                                   MeterRegistry meterRegistry,
                                   List<ReconciliationListener> listeners) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.listeners = listeners != null ? List.copyOf(listeners) : List.of();

        // 只注册支持对账的 Handler
        Map<String, ReconcilableHandler> handlerMap = new ConcurrentHashMap<>();
        if (handlerList != null) {
            handlerList.stream()
                    .filter(h -> h instanceof ReconcilableHandler)
                    .map(h -> (ReconcilableHandler) h)
                    .forEach(h -> handlerMap.put(h.getAggregateType(), h));
        }
        this.handlers = Collections.unmodifiableMap(handlerMap);

        // 并行线程池
        DataSyncProperties.ReconciliationConfig config = properties.getReconciliation();
        if (config.isParallelEnabled() && handlers.size() > 1) {
            this.executorService = Executors.newFixedThreadPool(
                    config.getThreadPoolSize(),
                    r -> {
                        Thread t = new Thread(r, "reconciliation-worker");
                        t.setDaemon(true);
                        return t;
                    }
            );
        } else {
            this.executorService = null;
        }

        // 生成唯一锁标识（避免误删其他实例的锁）
        this.lockValue = UUID.randomUUID().toString();

        // Metrics
        this.reconcileSuccessCounter = Counter.builder("datasync.reconcile.success")
                .description("Number of consistent items found during reconciliation")
                .register(meterRegistry);
        this.reconcileFailureCounter = Counter.builder("datasync.reconcile.failure")
                .description("Number of inconsistencies found during reconciliation")
                .register(meterRegistry);
        this.reconcileFixCounter = Counter.builder("datasync.reconcile.fix")
                .description("Number of auto-fixed inconsistencies")
                .register(meterRegistry);
    }

    /**
     * 定时对账任务
     */
    @Scheduled(cron = "${datasync.reconciliation.cron:0 0 3 * * ?}")
    public void reconcile() {
        if (!properties.getReconciliation().isEnabled()) {
            return;
        }

        // 防止重复执行
        if (!running.compareAndSet(false, true)) {
            log.warn("[Reconciliation] Task already running, skipping...");
            return;
        }

        try {
            if (!acquireLock()) {
                log.info("[Reconciliation] Failed to acquire distributed lock, another instance is running");
                return;
            }

            log.info("[Reconciliation] Starting data reconciliation task...");
            long startTime = System.currentTimeMillis();

            Map<String, ReconciliationResult> results = new ConcurrentHashMap<>();
            int totalChecked = 0;
            int totalFixed = 0;
            int totalFailed = 0;

            if (executorService != null) {
                // 并行对账
                List<Future<ReconciliationResult>> futures = new ArrayList<>();
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    ReconcilableHandler handler = entry.getValue();
                    futures.add(executorService.submit(() ->
                            reconcileAggregate(aggregateType, handler)));
                }

                int index = 0;
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    try {
                        ReconciliationResult result = futures.get(index++).get(5, TimeUnit.MINUTES);
                        results.put(aggregateType, result);
                        totalChecked += result.checked();
                        totalFixed += result.fixed();
                        totalFailed += result.failed();
                        log.info("[Reconciliation] {} - checked: {}, fixed: {}, failed: {}",
                                aggregateType, result.checked(), result.fixed(), result.failed());
                    } catch (Exception e) {
                        log.error("[Reconciliation] Error waiting for {}: {}",
                                aggregateType, e.getMessage(), e);
                        results.put(aggregateType, ReconciliationResult.empty());
                    }
                }
            } else {
                // 串行对账
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    ReconcilableHandler handler = entry.getValue();
                    try {
                        ReconciliationResult result = reconcileAggregate(aggregateType, handler);
                        results.put(aggregateType, result);
                        totalChecked += result.checked();
                        totalFixed += result.fixed();
                        totalFailed += result.failed();
                        log.info("[Reconciliation] {} - checked: {}, fixed: {}, failed: {}",
                                aggregateType, result.checked(), result.fixed(), result.failed());
                    } catch (Exception e) {
                        log.error("[Reconciliation] Error reconciling {}: {}",
                                aggregateType, e.getMessage(), e);
                        results.put(aggregateType, ReconciliationResult.empty());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Reconciliation] Completed in {}ms - total checked: {}, fixed: {}, failed: {}",
                    duration, totalChecked, totalFixed, totalFailed);

            // 通知监听器
            notifyListeners(results, totalChecked, totalFixed, totalFailed);

        } finally {
            running.set(false);
            releaseLock();
        }
    }

    /**
     * 对账单个聚合类型
     */
    private ReconciliationResult reconcileAggregate(String aggregateType, ReconcilableHandler handler) {
        long start = System.currentTimeMillis();

        try {
            ReconciliationReport report = handler.reconcile(
                    properties.getReconciliation().getBatchSize(),
                    properties.getReconciliation().isAutoFix()
            );

            long duration = System.currentTimeMillis() - start;

            // 修复 metrics 计算：success = checked - failed（一致的数据）
            int successCount = report.totalChecked() - report.inconsistentCount();
            reconcileSuccessCounter.increment(Math.max(0, successCount));
            reconcileFailureCounter.increment(report.inconsistentCount());
            reconcileFixCounter.increment(report.fixedCount());

            return ReconciliationResult.of(
                    report.totalChecked(),
                    report.fixedCount(),
                    report.failedCount(),
                    duration
            );

        } catch (Exception e) {
            log.error("[Reconciliation] Handler error for {}: {}", aggregateType, e.getMessage(), e);
            long duration = System.currentTimeMillis() - start;
            return ReconciliationResult.of(0, 0, 0, duration);
        }
    }

    /**
     * 获取分布式锁
     */
    private boolean acquireLock() {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script,
                    List.of(properties.getReconciliation().getLockKey()),
                    lockValue,
                    String.valueOf(properties.getReconciliation().getLockTtlSeconds()));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Reconciliation] Failed to acquire lock: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     */
    private void releaseLock() {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            redisTemplate.execute(script,
                    List.of(properties.getReconciliation().getLockKey()),
                    lockValue);
        } catch (Exception e) {
            log.error("[Reconciliation] Failed to release lock: {}", e.getMessage(), e);
        }
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners(Map<String, ReconciliationResult> results, int totalChecked, int totalFixed, int totalFailed) {
        if (listeners.isEmpty()) {
            return;
        }

        for (ReconciliationListener listener : listeners) {
            try {
                listener.onReconciliationComplete(results, totalFixed, totalFailed);
            } catch (Exception e) {
                log.error("[Reconciliation] Listener error: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取当前是否正在执行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取已注册的处理器数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
