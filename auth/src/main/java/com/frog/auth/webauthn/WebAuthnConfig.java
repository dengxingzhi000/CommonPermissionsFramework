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
}
