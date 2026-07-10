package com.frog.common.integration.sync.reconciliation;

import java.util.Map;

/**
 * 对账结果监听器
 * <p>
 * 业务方可实现此接口，在对账完成后收到通知
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface ReconciliationListener {

    /**
     * 对账完成回调
     *
     * @param results    各聚合类型的对账结果
     * @param totalFixed 总修复数量
     * @param totalFailed 总失败数量
     */
    void onReconciliationComplete(Map<String, ReconciliationResult> results, int totalFixed, int totalFailed);

    /**
     * 对账异常回调
     *
     * @param aggregateType 聚合类型
     * @param error         异常信息
     */
    void onReconciliationError(String aggregateType, Throwable error);
}
