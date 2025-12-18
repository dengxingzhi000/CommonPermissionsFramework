package com.frog.auth.webauthn;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WebAuthn 配置
 *
 * @author Deng
 * @since 2025-12-15
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "webauthn.rp")
public class WebAuthnConfig {

    /**
     * Relying Party ID (通常是域名)
     */
    private String id = "localhost";

    /**
     * Relying Party Name (显示名称)
     */
    private String name = "CommonPermissionsFramework";

    /**
     * Relying Party Origin (完整的 origin URL)
     */
    private String origin = "https://localhost";

    /**
     * 是否要求用户验证
     */
    private boolean userVerificationRequired = false;

    /**
     * 支持的认证器附件类型
     * platform - 仅平台认证器 (如 TouchID, FaceID)
     * cross-platform - 仅跨平台认证器 (如 YubiKey)
     * 空 - 两者都支持
     */
    private String authenticatorAttachment;

    /**
     * 支持的 resident key 要求
     * required, preferred, discouraged
     */
    private String residentKey = "preferred";

    // ==================== 超时与生命周期配置 ====================

    /**
     * 认证挑战过期时间（秒）
     * 默认 120 秒（2 分钟）
     */
    private long challengeExpirySeconds = 120L;

    /**
     * 注册挑战过期时间（秒）
     * 默认 300 秒（5 分钟），注册流程通常需要更长时间
     */
    private long registrationChallengeExpirySeconds = 300L;

    /**
     * 凭证不活跃阈值（天）
     * 超过此天数未使用的凭证将被标记为不健康
     * 默认 90 天
     */
    private long credentialInactiveDays = 90L;

    /**
     * 认证尝试日志保留时间（天）
     * 默认 30 天
     */
    private long authAttemptRetentionDays = 30L;
}
