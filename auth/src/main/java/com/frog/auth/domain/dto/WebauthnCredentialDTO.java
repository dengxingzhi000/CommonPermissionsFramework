package com.frog.auth.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebAuthn 凭证响应DTO
 * <p>
 * 用于API响应，隐藏敏感信息（如公钥）
 * 参考Google Passkey API设计
 *
 * @author system
 * @since 2025-11-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "WebAuthn 凭证信息")
public class WebauthnCredentialDTO {
    @Schema(description = "凭证 ID", example = "KSjKz3HHnUhFIAoS4RFCw")
    private String credentialId;

    @Schema(description = "设备名称", example = "MacBook Pro TouchID")
    private String deviceName;

    @Schema(description = "签名算法", example = "ES256")
    private String algorithm;

    @Schema(description = "认证器 GUID", example = "08987058-cadc-4b81-b6e1-30de50dcbe96")
    private UUID aaguid;

    @Schema(description = "传输方式", example = "usb,nfc,internal")
    private String transports;

    @Schema(description = "认证器类型", example = "platform")
    private String authenticatorAttachment;

    @Schema(description = "是否启用", example = "true")
    private Boolean isActive;

    @Schema(description = "备份状态", example = "false")
    private Boolean backupState;

    @Schema(description = "最后使用时间", example = "2025-11-27T10:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUsedAt;

    @Schema(description = "创建时间", example = "2025-11-27T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}