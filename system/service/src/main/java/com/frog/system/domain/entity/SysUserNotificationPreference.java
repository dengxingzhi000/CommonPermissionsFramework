package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 用户通知偏好表
 *
 * @author Deng
 * @since 2025-12-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_user_notification_preference")
@Tag(
        name = "SysUserNotificationPreference 对象",
        description = "用户通知偏好表"
)
public class SysUserNotificationPreference implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "用户 ID（跨库关联 db_user.sys_user）")
    @TableField("user_id")
    private UUID userId;

    @Schema(description = "通知类型:APPROVAL,SECURITY,SYSTEM,MARKETING")
    @TableField("notification_type")
    private String notificationType;

    @Schema(description = "通知渠道:EMAIL,SMS,WECHAT,DINGTALK,FEISHU,PUSH")
    @TableField("channel")
    private String channel;

    @Schema(description = "是否启用")
    @TableField("enabled")
    private Boolean enabled;

    @Schema(description = "免打扰开始时间")
    @TableField("quiet_hours_start")
    private LocalTime quietHoursStart;

    @Schema(description = "免打扰结束时间")
    @TableField("quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 通知类型枚举
     */
    @Getter
    public enum NotificationType {
        APPROVAL("APPROVAL", "审批通知"),
        SECURITY("SECURITY", "安全通知"),
        SYSTEM("SYSTEM", "系统通知"),
        MARKETING("MARKETING", "营销通知");

        private final String code;
        private final String desc;

        NotificationType(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }

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
     * 判断当前时间是否在免打扰时段
     */
    public boolean isQuietHour() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        LocalTime now = LocalTime.now();
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            // 正常时段，例如 22:00 - 08:00
            return now.isAfter(quietHoursStart) && now.isBefore(quietHoursEnd);
        } else {
            // 跨午夜时段，例如 22:00 - 08:00（次日）
            return now.isAfter(quietHoursStart) || now.isBefore(quietHoursEnd);
        }
    }
}