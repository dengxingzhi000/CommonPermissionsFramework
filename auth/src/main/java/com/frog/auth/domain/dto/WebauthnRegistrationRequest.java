package com.frog.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * WebAuthn 注册请求DTO
 * <p>
 * 用于注册新的WebAuthn凭证
 * 参考FIDO2规范和Google Passkey实现
 *
 * @author system
 * @since 2025-11-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebAuthn 注册请求")
public class WebauthnRegistrationRequest {
    @Schema(description = "凭证ID(base64url)", example = "KSjKz3HHnUhFIAoS4RFCw", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "凭证 ID不能为空")
    @Size(min = 16, max = 1024, message = "凭证ID长度必须在16-1024之间")
    private String credentialId;

    @Schema(description = "客户端数据JSON(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "clientDataJSON 不能为空")
    private String clientDataJSON;

    @Schema(description = "证明对象(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "attestationObject 不能为空")
    private String attestationObject;

    @Schema(description = "设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备 ID不能为空")
    private String deviceId;

    @Schema(description = "公钥(PEM格式，可选，用于简化路径)")
    @Size(max = 2048, message = "公钥长度不能超过2048")
    private String publicKeyPem;

    @Schema(description = "签名算法", example = "ES256", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "签名算法不能为空")
    @Pattern(regexp = "^(ES256|ES384|ES512|RS256|RS384|RS512|PS256|PS384|PS512|EdDSA)$",
             message = "不支持的签名算法")
    private String algorithm;

    @Schema(description = "设备名称", example = "我的 iPhone")
    @Size(max = 100, message = "设备名称长度不能超过100")
    private String deviceName;

    @Schema(description = "认证器 GUID", example = "08987058-cadc-4b81-b6e1-30de50dcbe96")
    private UUID aaguid;

    @Schema(description = "传输方式(逗号分隔)", example = "internal")
    @Size(max = 100, message = "传输方式长度不能超过100")
    private String transports;

    @Schema(description = "认证器类型", example = "platform")
    @Pattern(regexp = "^(platform|cross-platform)$", message = "认证器类型不正确")
    private String authenticatorAttachment;

    @Schema(description = "用户验证方法", example = "required")
    @Pattern(regexp = "^(required|preferred|discouraged)$", message = "用户验证方法不正确")
    private String userVerification;

    @Schema(description = "备份状态", example = "false")
    private Boolean backupState;

    @Schema(description = "备份资格", example = "true")
    private Boolean backupEligible;
}