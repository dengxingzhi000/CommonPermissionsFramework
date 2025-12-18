package com.frog.common.integration.sync.publisher;

import com.frog.common.integration.sync.event.DataSyncEvent;

import java.util.List;

/**
 * 数据同步事件发布器接口
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface DataSyncPublisher {
    /**
     * 同步发布事件
     *
     * @param event 同步事件
     * @return 是否发布成功
     */
    boolean publish(DataSyncEvent event);

    /**
     * 异步发布事件（fire-and-forget）
     * <p>
     * 内部使用 CompletableFuture 处理回调，但不暴露给调用方
     *
     * @param event 同步事件
     */
    void publishAsync(DataSyncEvent event);

    /**
     * 批量发布事件
     *
     * @param events 事件列表
     * @return 成功数量
     */
    int publishBatch(List<DataSyncEvent> events);

    /**
     * 发布到死信队列
     *
     * @param event 失败事件
     * @param reason 失败原因
     */
    void publishToDeadLetter(DataSyncEvent event, String reason);
}
