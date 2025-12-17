package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
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
 * OAuth 第三方登录绑定表
 *
 * @author Deng
 * @since 2025-12-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "sys_user_oauth", autoResultMap = true)
@Tag(
        name = "SysUserOauth 对象",
        description = "OAuth 第三方登录绑定表"
)
public class SysUserOauth implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "用户 ID")
    private UUID userId;

    @Schema(description = "OAuth提供商:google,github,apple,wechat,dingtalk,feishu")
    private String provider;

    @Schema(description = "OAuth开放ID(唯一标识)")
    private String oauthOpenid;

    @Schema(description = "OAuth联合ID(用于同一平台多应用)")
    private String oauthUnionId;

    @Schema(description = "OAuth 邮箱")
    private String oauthEmail;

    @Schema(description = "OAuth 昵称")
    private String oauthNickname;

    @Schema(description = "OAuth 头像URL")
    private String oauthAvatar;

    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "令牌过期时间")
    private LocalDateTime tokenExpireTime;

    @Schema(description = "OAuth返回的原始用户信息(JSONB)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawUserInfo;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "逻辑删除")
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    /**
     * OAuth 提供商枚举
     */
    @Getter
    public enum Provider {
        GOOGLE("google"),
        GITHUB("github"),
        APPLE("apple"),
        WECHAT("wechat"),
        DINGTALK("dingtalk"),
        FEISHU("feishu");

        private final String code;

        Provider(String code) {
            this.code = code;
        }
    }
}
