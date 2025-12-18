package com.frog.common.integration.sync.handler;

import com.frog.common.integration.sync.event.DataSyncEvent;
import lombok.Getter;

/**
 * 数据同步处理器接口
 * <p>
 * 每个聚合类型实现自己的处理器
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface DataSyncHandler {
    /**
     * 获取处理的聚合类型
     *
     * @return 聚合类型（如 User, Dept, Role）
     */
    String getAggregateType();

    /**
     * 处理同步事件
     *
     * @param event 同步事件
     * @throws DataSyncException 处理失败时抛出
     */
    void handle(DataSyncEvent event) throws DataSyncException;

    /**
     * 全量同步（对账修复时调用）
     *
     * @param primaryId 主键 ID
     */
    default void fullSync(String primaryId) {
        // 默认空实现
    }

    /**
     * 数据同步异常
     */
    @Getter
    class DataSyncException extends RuntimeException {
        private final boolean retryable;

        public DataSyncException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public DataSyncException(String message, Throwable cause, boolean retryable) {
            super(message, cause);
            this.retryable = retryable;
        }
    }
}
