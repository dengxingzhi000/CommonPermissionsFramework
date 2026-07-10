package com.frog.common.integration.sync.reconciliation;

import com.frog.common.integration.sync.handler.DataSyncHandler;
import com.frog.common.integration.sync.reconciliation.DataReconciliationTask.ReconciliationReport;

/**
 * 可对账的处理器接口
 * <p>
 * Handler 如果支持对账，需要实现此接口
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface ReconcilableHandler extends DataSyncHandler {

    /**
     * 执行对账
     *
     * @param batchSize 批次大小
     * @param autoFix   是否自动修复
     * @return 对账报告
     */
    ReconciliationReport reconcile(int batchSize, boolean autoFix);
}