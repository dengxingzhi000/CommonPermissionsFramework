package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persistence model for notification send attempts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_notification_audit")
public class NotificationAuditLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("reference_id")
    private String referenceId;

    @TableField("channel")
    private String channel;

    @TableField("status")
    private String status;

    @TableField("subject")
    private String subject;

    @TableField("username")
    private String username;

    @TableField("email")
    private String email;

    @TableField("template_code")
    private String templateCode;

    @TableField("content")
    private String content;

    @TableField("variables_json")
    private String variablesJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private LocalDateTime createdAt;
}

