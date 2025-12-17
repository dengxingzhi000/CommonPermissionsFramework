package com.frog.auth.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebAuthn 认证请求DTO
 * <p>
 * 用于验证 WebAuthn凭证
 *
 * @author system
 * @since 2025-11-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebAuthn 认证请求")
public class WebauthnAuthenticationRequest {
    @Schema(description = "凭证 ID", example = "KSjKz3HHnUhFIAoS4RFCw", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "凭证 ID不能为空")
    private String credentialId;

    @Schema(description = "客户端数据JSON(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "客户端数据不能为空")
    private String clientDataJSON;

    @Schema(description = "认证器数据(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "认证器数据不能为空")
    private String authenticatorData;

    @Schema(description = "签名(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "签名不能为空")
    private String signature;

    @Schema(description = "用户句柄(base64url,可选)")
    private String userHandle;

    @Schema(description = "新的签名计数器", example = "43", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "签名计数器不能为空")
    private Long signCount;
}