package com.frog.common.integration.sync.reconciliation;

/**
 * 对账结果（不可变）
 *
 * @param checked  检查总数
 * @param fixed    修复数量
 * @param failed   失败数量
 * @param duration 执行耗时（毫秒）
 * @author Deng
 * @since 2025-12-16
 */
public record ReconciliationResult(
        int checked,
        int fixed,
        int failed,
        long duration
) {
    public static ReconciliationResult empty() {
        return new ReconciliationResult(0, 0, 0, 0);
    }

    public static ReconciliationResult of(int checked, int fixed, int failed, long duration) {
        return new ReconciliationResult(checked, fixed, failed, duration);
    }
}
