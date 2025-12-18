package com.frog.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebAuthn 凭证实体类
 * <p>
 * 参考标准：
 * - W3C Web Authentication API Level 2
 * - FIDO2 CTAP2 Protocol
 * - Google Passkey Implementation

 * 安全特性：
 * - 凭证ID全局唯一，防止凭证碰撞
 * - 签名计数器防重放攻击
 * - 公钥隔离存储，降低泄露风险
 * - 支持多设备绑定，提升用户体验

 * 业务外键: user_id -> sys_user.user_id (应用层保证完整性)
 *
 * @author system
 * @since 2025-11-27
 */
@Data
@EqualsAndHashCode(callSuper = false, of = {"credentialId"})
@Accessors(chain = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(
        value = "webauthn_credential",
        autoResultMap = true
)
@Tag(
        name = "WebauthnCredential",
        description = "WebAuthn 凭证实体"
)
public class WebauthnCredential implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private UUID id;

    /**
     * WebAuthn凭证ID (Credential ID)
     * Base64URL编码的凭证标识符，由认证器生成，全局唯一
     * 用于客户端查找对应的私钥进行签名
     */
    @Schema(description = "WebAuthn凭证ID(base64url编码)",
            example = "KSjKz3HHnUhFIAoS4RFCw",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField(value = "credential_id")
    @NotBlank(message = "凭证 ID不能为空")
    @Size(min = 16, max = 1024, message = "凭证ID长度必须在16-1024之间")
    private String credentialId;

    /**
     * 用户ID
     * 业务外键，关联到sys_user表
     */
    @Schema(description = "用户 ID", example = "user_123456", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField(value = "user_id")
    @NotBlank(message = "用户 ID不能为空")
    @Size(max = 64, message = "用户ID长度不能超过64")
    private UUID userId;

    /**
     * 公钥 (Public Key)
     * 存储PEM格式的公钥，用于验证认证器签名
     * 建议：生产环境应加密存储或使用HSM
     */
    @Schema(description = "公钥(PEM格式)", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField(value = "public_key_pem")
    @JsonIgnore // 敏感信息，不在JSON响应中暴露
    @NotBlank(message = "公钥不能为空")
    @Size(max = 2048, message = "公钥长度不能超过2048")
    private String publicKeyPem;

    /**
     * 签名算法 (COSE Algorithm)
     * 常见值：
     * - ES256 (-7): ECDSA with SHA-256
     * - RS256 (-257): RSASSA-PKCS1-v1_5 with SHA-256
     * - EdDSA (-8): EdDSA
     */
    @Schema(description = "签名算法(COSE Algorithm)",
            example = "ES256",
            allowableValues = {"ES256", "RS256", "EdDSA", "ES384", "ES512"})
    @TableField(value = "alg")
    @NotBlank(message = "签名算法不能为空")
    @Pattern(regexp = "^(ES256|ES384|ES512|RS256|RS384|RS512|PS256|PS384|PS512|EdDSA)$",
             message = "不支持的签名算法")
    private String alg;

    /**
     * 签名计数器 (Signature Counter)
     * 防重放攻击的关键机制
     * - 每次认证后必须递增
     * - 如果计数器不递增或回退，说明可能存在克隆攻击
     * - 初始值通常为0或1
     */
    @Schema(description = "签名计数器(防重放)", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField(value = "sign_count")
    @NotNull(message = "签名计数器不能为空")
    @Min(value = 0, message = "签名计数器不能为负数")
    private Long signCount;

    /**
     * 设备名称 (用户自定义)
     * 帮助用户识别不同的认证器
     */
    @Schema(description = "设备名称(用户自定义)",
            example = "MacBook Pro TouchID")
    @TableField(value = "device_name")
    @Size(max = 100, message = "设备名称长度不能超过100")
    private String deviceName;

    /**
     * aaGUID (Authenticator Attestation GUID)
     * 识别认证器型号的唯一标识符
     * 格式：UUID (例如：08987058-cadc-4b81-b6e1-30de50dcbe96)
     */
    @Schema(description = "认证器GUID(识别设备型号)",
            example = "08987058-cadc-4b81-b6e1-30de50dcbe96")
    @TableField(value = "aaguid")
    private UUID aaguid;

    /**
     * 传输方式 (Transports)
     * 认证器支持的通信协议
     * 可选值：usb, nfc, ble, internal, hybrid
     * 存储格式：PostgreSQL TEXT[] 数组
     */
    @Schema(description = "支持的传输方式",
            example = "[\"usb\", \"internal\"]",
            allowableValues = {"usb", "nfc", "ble", "internal", "hybrid"})
    @TableField(value = "transports", typeHandler = com.frog.common.mybatisPlus.handler.StringArrayTypeHandler.class)
    private String[] transports;

    /**
     * 凭证状态
     * - true: 激活，可用于认证
     * - false: 停用，用户主动禁用或系统检测到异常
     */
    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField(value = "is_active")
    @NotNull(message = "启用状态不能为空")
    private Boolean isActive;

    /**
     * 最后使用时间
     * 用于：
     * - 清理长期未使用的凭证
     * - 检测异常登录模式
     * - 生成用户活跃度报告
     */
    @Schema(description = "最后使用时间", example = "2025-11-27T10:30:00")
    @TableField(value = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * 创建时间
     * 自动填充，用于审计和统计
     */
    @Schema(description = "创建时间", example = "2025-11-27T10:00:00")
    @TableField(value = "created_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     * 自动填充，记录最后修改时间
     */
    @Schema(description = "更新时间", example = "2025-11-27T10:30:00")
    @TableField(value = "updated_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 检查凭证是否可用
     *
     * @return true if credential is active and not expired
     */
    public boolean isAvailable() {
        return Boolean.TRUE.equals(this.isActive);
    }

    /**
     * 检查签名计数器是否有效
     * 用于检测克隆攻击
     *
     * @param newCounter 新的计数器值
     * @return true if counter is valid (increasing)
     */
    public boolean isCounterValid(Long newCounter) {
        if (newCounter == null || this.signCount == null) {
            return false;
        }
        // 计数器必须严格递增 (除非设备不支持计数器，此时始终为0)
        return newCounter > this.signCount || (newCounter == 0 && this.signCount == 0);
    }

    /**
     * 更新最后使用时间和签名计数器
     *
     * @param newCounter 新的签名计数器值
     */
    public void updateUsage(Long newCounter) {
        this.lastUsedAt = LocalDateTime.now();
        this.signCount = newCounter;
    }

    /**
     * 停用凭证
     * 用于用户主动删除或系统检测到异常
     */
    public void deactivate() {
        this.isActive = false;
        this.updateTime = LocalDateTime.now();
    }
}
