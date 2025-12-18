package com.frog.common.integration.sync.event;

/**
 * 数据同步事件类型
 *
 * @author Deng
 * @since 2025-12-16
 */
public enum DataSyncEventType {
    /**
     * 新增
     */
    INSERT,

    /**
     * 更新
     */
    UPDATE,

    /**
     * 删除
     */
    DELETE,

    /**
     * 批量更新
     */
    BATCH_UPDATE,

    /**
     * 全量同步
     */
    FULL_SYNC
}
