package com.frog.common.integration.sync.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 数据同步事件（全局通用）
 * <p>
 * 设计参考：
 * - 阿里 Canal binlog 事件格式
 * - CloudEvents 规范
 * - 字节跳动数据同步中间件
 *
 * @author Deng
 * @since 2025-12-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件唯一ID（用于幂等处理）
     */
    private String eventId;

    /**
     * 事件类型
     */
    private DataSyncEventType eventType;

    /**
     * 事件发生时间
     */
    private Instant eventTime;

    /**
     * 事件版本（乐观锁）
     */
    private Long version;

    /**
     * 源服务名称
     */
    private String sourceService;

    /**
     * 源数据库
     */
    private String sourceDatabase;

    /**
     * 源表名
     */
    private String sourceTable;

    /**
     * 聚合类型（User, Dept, Role 等）
     */
    private String aggregateType;

    /**
     * 主键 ID
     */
    private String primaryId;

    /**
     * 变更前数据
     */
    private Map<String, Object> beforeData;

    /**
     * 变更后数据
     */
    private Map<String, Object> afterData;

    /**
     * 变更字段列表
     */
    private String[] changedFields;

    /**
     * 分布式追踪 ID
     */
    private String traceId;

    /**
     * Span ID
     */
    private String spanId;

    /**
     * 操作用户 ID
     */
    private String operatorId;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 重试次数
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 首次失败时间
     */
    private Instant firstFailureTime;

    /**
     * 最后失败原因
     */
    private String lastFailureReason;

    // ==================== 工厂方法 ====================

    /**
     * 创建事件
     */
    public static DataSyncEvent create(String aggregateType, String primaryId,
                                        DataSyncEventType eventType,
                                        Map<String, Object> data) {
        return DataSyncEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventTime(Instant.now())
                .aggregateType(aggregateType)
                .primaryId(primaryId)
                .afterData(data)
                .version(1L)
                .build();
    }

    /**
     * 创建插入事件
     */
    public static DataSyncEvent ofInsert(String aggregateType, String primaryId,
                                          Map<String, Object> data) {
        return create(aggregateType, primaryId, DataSyncEventType.INSERT, data);
    }

    /**
     * 创建更新事件
     */
    public static DataSyncEvent ofUpdate(String aggregateType, String primaryId,
                                          Map<String, Object> beforeData,
                                          Map<String, Object> afterData,
                                          String... changedFields) {
        return DataSyncEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(DataSyncEventType.UPDATE)
                .eventTime(Instant.now())
                .aggregateType(aggregateType)
                .primaryId(primaryId)
                .beforeData(beforeData)
                .afterData(afterData)
                .changedFields(changedFields)
                .version(1L)
                .build();
    }

    /**
     * 创建删除事件
     */
    public static DataSyncEvent ofDelete(String aggregateType, String primaryId) {
        return create(aggregateType, primaryId, DataSyncEventType.DELETE, null);
    }

    // ==================== 辅助方法 ====================

    /**
     * 增加重试次数
     */
    public void incrementRetry(String failureReason) {
        this.retryCount++;
        this.lastFailureReason = failureReason;
        if (this.firstFailureTime == null) {
            this.firstFailureTime = Instant.now();
        }
    }

    /**
     * 是否可重试
     */
    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    /**
     * 获取分区键（用于 Kafka 分区）
     */
    public String getPartitionKey() {
        return this.aggregateType + ":" + this.primaryId;
    }

    /**
     * 获取主题名称
     */
    public String getTopicName(String prefix) {
        return prefix + "." + this.aggregateType.toLowerCase();
    }
}
