package com.frog.common.integration.sync.reconciliation;

/**
 * 对账报告（不可变）
 *
 * @param totalChecked     检查总数
 * @param inconsistentCount 不一致数量
 * @param fixedCount       修复数量
 * @param failedCount      失败数量
 * @author Deng
 * @since 2025-12-16
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
