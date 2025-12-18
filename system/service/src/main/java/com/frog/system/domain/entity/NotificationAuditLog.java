package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 通知发送审计表
 *
 * @author Deng
 * @since 2025-12-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "sys_notification_audit", autoResultMap = true)
@Tag(
        name = "NotificationAuditLog 对象",
        description = "通知发送审计表"
)
public class NotificationAuditLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "业务关联 ID")
    @TableField("reference_id")
    private String referenceId;

    @Schema(description = "接收用户 ID")
    @TableField("user_id")
    private UUID userId;

    @Schema(description = "通知渠道:EMAIL,SMS,WECHAT,DINGTALK,FEISHU,PUSH")
    @TableField("channel")
    private String channel;

    @Schema(description = "发送状态:PENDING,SENT,FAILED,CANCELLED")
    @TableField("status")
    private String status;

    @Schema(description = "通知主题")
    @TableField("subject")
    private String subject;

    @Schema(description = "用户名")
    @TableField("username")
    private String username;

    @Schema(description = "邮箱")
    @TableField("email")
    private String email;

    @Schema(description = "手机号")
    @TableField("phone")
    private String phone;

    @Schema(description = "模板编码")
    @TableField("template_code")
    private String templateCode;

    @Schema(description = "通知内容")
    @TableField("content")
    private String content;

    @Schema(description = "模板变量(JSONB)")
    @TableField(value = "variables", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    @Schema(description = "错误信息")
    @TableField("error_message")
    private String errorMessage;

    @Schema(description = "重试次数")
    @TableField("retry_count")
    private Integer retryCount;

    @Schema(description = "最大重试次数")
    @TableField("max_retries")
    private Integer maxRetries;

    @Schema(description = "下次重试时间")
    @TableField("next_retry_time")
    private LocalDateTime nextRetryTime;

    @Schema(description = "发送时间")
    @TableField("sent_at")
    private LocalDateTime sentAt;

    @Schema(description = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ==================== 冗余字段 ====================

    @Schema(description = "用户真实姓名（冗余字段）")
    @TableField("real_name")
    private String realName;

    /**
     * 通知渠道枚举
     */
    @Getter
    public enum Channel {
        EMAIL("EMAIL", "邮件"),
        SMS("SMS", "短信"),
        WECHAT("WECHAT", "微信"),
        DINGTALK("DINGTALK", "钉钉"),
        FEISHU("FEISHU", "飞书"),
        PUSH("PUSH", "推送");

        private final String code;
        private final String desc;

        Channel(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

    /**
     * 发送状态枚举
     */
    @Getter
    public enum Status {
        PENDING("PENDING", "待发送"),
        SENT("SENT", "已发送"),
        FAILED("FAILED", "发送失败"),
        CANCELLED("CANCELLED", "已取消");

        private final String code;
        private final String desc;

        Status(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}