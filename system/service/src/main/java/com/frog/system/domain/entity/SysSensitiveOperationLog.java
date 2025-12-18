package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.frog.common.mybatisPlus.handler.UuidArrayTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 敏感操作日志表
 *
 * @author Deng
 * @since 2025-12-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "sys_sensitive_operation_log", autoResultMap = true)
@Tag(
        name = "SysSensitiveOperationLog 对象",
        description = "敏感操作日志表"
)
public class SysSensitiveOperationLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "用户 ID")
    private UUID userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "操作类型:EXPORT,BULK_UPDATE,BULK_DELETE,DATA_DOWNLOAD,PERMISSION_CHANGE")
    private String operationType;

    @Schema(description = "操作模块")
    private String operationModule;

    @Schema(description = "敏感数据类型:PERSONAL_INFO,FINANCIAL,MEDICAL,SECRET")
    private String sensitiveDataType;

    @Schema(description = "数据指纹(SHA256,不存储原始数据)")
    private String dataFingerprint;

    @Schema(description = "影响记录数")
    private Integer affectedCount;

    @Schema(description = "目标表名")
    private String targetTable;

    @Schema(description = "影响的记录 ID列表")
    @TableField(typeHandler = UuidArrayTypeHandler.class)
    private UUID[] targetIds;

    @Schema(description = "操作详情(JSONB)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> operationDetail;

    @Schema(description = "是否需要审批")
    private Boolean approvalRequired;

    @Schema(description = "审批 ID")
    private UUID approvalId;

    @Schema(description = "风险评分:1-10")
    private Integer riskScore;

    @Schema(description = "IP 地址")
    private String ipAddress;

    @Schema(description = "用户代理")
    private String userAgent;

    @Schema(description = "设备指纹")
    private String deviceFingerprint;

    @Schema(description = "地理位置")
    private String location;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ==================== 冗余字段 ====================

    @Schema(description = "用户真实姓名（冗余字段）")
    @TableField("real_name")
    private String realName;

    @Schema(description = "部门名称（冗余字段）")
    @TableField("dept_name")
    private String deptName;

    /**
     * 操作类型枚举
     */
    @Getter
    public enum OperationType {
        EXPORT("EXPORT", "数据导出"),
        BULK_UPDATE("BULK_UPDATE", "批量更新"),
        BULK_DELETE("BULK_DELETE", "批量删除"),
        DATA_DOWNLOAD("DATA_DOWNLOAD", "数据下载"),
        PERMISSION_CHANGE("PERMISSION_CHANGE", "权限变更");

        private final String code;
        private final String desc;

        OperationType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    /**
     * 敏感数据类型枚举
     */
    @Getter
    public enum SensitiveDataType {
        PERSONAL_INFO("PERSONAL_INFO", "个人信息"),
        FINANCIAL("FINANCIAL", "财务信息"),
        MEDICAL("MEDICAL", "医疗信息"),
        SECRET("SECRET", "机密信息");

        private final String code;
        private final String desc;

        SensitiveDataType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}
