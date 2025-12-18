package com.frog.common.integration.sync.reconciliation;

import com.frog.common.integration.sync.config.DataSyncProperties;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final DataSyncProperties properties;
    private final Map<String, DataSyncHandler> handlers = new ConcurrentHashMap<>();

    // Metrics
    private final Counter reconcileSuccessCounter;
    private final Counter reconcileFailureCounter;
    private final Counter reconcileFixCounter;

    public DataReconciliationTask(DataSyncProperties properties,
                                   List<DataSyncHandler> handlerList,
                                   MeterRegistry meterRegistry) {
        this.properties = properties;

        if (handlerList != null) {
            handlerList.forEach(h -> handlers.put(h.getAggregateType(), h));
        }

        this.reconcileSuccessCounter = Counter.builder("datasync.reconcile.success")
                .description("Number of successful reconciliation checks")
                .register(meterRegistry);
        this.reconcileFailureCounter = Counter.builder("datasync.reconcile.failure")
                .description("Number of reconciliation failures found")
                .register(meterRegistry);
        this.reconcileFixCounter = Counter.builder("datasync.reconcile.fix")
                .description("Number of auto-fixed inconsistencies")
                .register(meterRegistry);
    }

    /**
     * 定时对账任务
     * <p>
     * cron 表达式由配置控制，默认每天凌晨 3 点
     */
    @Scheduled(cron = "${datasync.reconciliation.cron:0 0 3 * * ?}")
    public void reconcile() {
        if (!properties.getReconciliation().isEnabled()) {
            return;
        }

        log.info("[Reconciliation] Starting data reconciliation task...");
        long startTime = System.currentTimeMillis();

        int totalChecked = 0;
        int totalFixed = 0;
        int totalFailed = 0;

        for (Map.Entry<String, DataSyncHandler> entry : handlers.entrySet()) {
            String aggregateType = entry.getKey();
            DataSyncHandler handler = entry.getValue();

            try {
                ReconciliationResult result = reconcileAggregate(aggregateType, handler);
                totalChecked += result.checked;
                totalFixed += result.fixed;
                totalFailed += result.failed;

                log.info("[Reconciliation] {} - checked: {}, fixed: {}, failed: {}",
                        aggregateType, result.checked, result.fixed, result.failed);

            } catch (Exception e) {
                log.error("[Reconciliation] Error reconciling {}: {}",
                        aggregateType, e.getMessage(), e);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Reconciliation] Completed in {}ms - total checked: {}, fixed: {}, failed: {}",
                duration, totalChecked, totalFixed, totalFailed);
    }

    /**
     * 对账单个聚合类型
     */
    private ReconciliationResult reconcileAggregate(String aggregateType,
                                                     DataSyncHandler handler) {
        ReconciliationResult result = new ReconciliationResult();

        // 这里需要实现具体的对账逻辑：
        // 1. 从源库获取数据
        // 2. 从目标库获取冗余数据
        // 3. 比对差异
        // 4. 如果启用自动修复，调用 handler.fullSync() 修复

        // 由于具体的对账逻辑依赖业务表结构，
        // 这里只提供框架，具体实现由 Handler 自己负责

        if (handler instanceof ReconcilableHandler reconcilableHandler) {
            try {
                ReconciliationReport report = reconcilableHandler.reconcile(
                        properties.getReconciliation().getBatchSize(),
                        properties.getReconciliation().isAutoFix()
                );

                result.checked = report.totalChecked();
                result.fixed = report.fixedCount();
                result.failed = report.failedCount();

                reconcileSuccessCounter.increment(result.checked - result.failed);
                reconcileFailureCounter.increment(result.failed);
                reconcileFixCounter.increment(result.fixed);

            } catch (Exception e) {
                log.error("[Reconciliation] Handler error: {}", e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * 对账结果
     */
    private static class ReconciliationResult {
        int checked = 0;
        int fixed = 0;
        int failed = 0;
    }

    /**
     * 可对账的处理器接口
     * <p>
     * Handler 如果支持对账，需要实现此接口
     */
    public interface ReconcilableHandler {
        ReconciliationReport reconcile(int batchSize, boolean autoFix);
    }

    /**
     * 对账报告
     */
    public record ReconciliationReport(
            int totalChecked,
            int inconsistentCount,
            int fixedCount,
            int failedCount
    ) {
        public static ReconciliationReport empty() {
            return new ReconciliationReport(0, 0, 0, 0);
        }
    }
}
