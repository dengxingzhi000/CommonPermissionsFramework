package com.frog.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.frog.auth.domain.dto.WebauthnAuthenticationRequest;
import com.frog.auth.domain.dto.WebauthnCredentialDTO;
import com.frog.auth.domain.dto.WebauthnRegistrationRequest;
import com.frog.auth.domain.entity.WebauthnCredential;
import com.frog.common.dto.auth.TokenUpgradeResponse;
import com.frog.common.dto.auth.WebAuthnChallengeResponse;
import com.frog.common.dto.auth.WebAuthnRegisterChallengeResponse;

import java.util.List;
import java.util.UUID;

/**
 * WebAuthn凭证服务接口
 *
 * @author Deng
 * createData 2025/11/13 10:46
 * @version 1.0
 */
public interface IWebauthnCredentialService extends IService<WebauthnCredential> {
    /**
     * 生成注册挑战
     * <p>
     * 参考：WebAuthn Level 2 - navigator.credentials.create()
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param deviceId 设备ID
     * @param rpId     依赖方ID (域名)
     * @return 注册挑战响应
     */
    WebAuthnRegisterChallengeResponse generateRegistrationChallenge(
            UUID userId, String username, String deviceId, String rpId);

    /**
     * 验证并注册凭证
     * <p>
     * 安全检查：
     * - 验证挑战是否有效且未过期
     * - 验证证明(attestation)签名
     * - 检查凭证ID是否已存在
     * - 验证RP ID和Origin
     *
     * @param userId  用户ID
     * @param request 注册请求
     * @return 注册的凭证DTO
     */
    WebauthnCredentialDTO registerCredential(UUID userId, WebauthnRegistrationRequest request);

    /**
     * 生成认证挑战
     * <p>
     * 参考：WebAuthn Level 2 - navigator.credentials.get()
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param deviceId 设备ID
     * @param rpId     依赖方ID
     * @return 认证挑战响应
     */
    WebAuthnChallengeResponse generateAuthenticationChallenge(
            UUID userId, String username, String deviceId, String rpId);

    /**
     * 验证认证请求并升级Token
     * <p>
     * 安全检查：
     * - 验证挑战是否有效且未过期
     * - 验证签名计数器防止克隆攻击
     * - 验证断言(assertion)签名
     * - 检查凭证是否激活
     *
     * @param userId    用户ID
     * @param username  用户名
     * @param request   认证请求
     * @param deviceId  设备ID
     * @param ipAddress IP地址
     * @return Token升级响应
     */
    TokenUpgradeResponse authenticateAndUpgradeToken(
            UUID userId, String username, WebauthnAuthenticationRequest request,
            String deviceId, String ipAddress);

    /**
     * 获取用户所有激活的凭证
     *
     * @param userId 用户ID
     * @return 凭证DTO列表
     */
    List<WebauthnCredentialDTO> listActiveCredentials(UUID userId);

    /**
     * 更新凭证设备名称
     *
     * @param userId       用户ID
     * @param credentialId 凭证ID
     * @param deviceName   新设备名称
     * @return 更新后的凭证DTO
     */
    WebauthnCredentialDTO updateDeviceName(UUID userId, String credentialId, String deviceName);

    /**
     * 停用凭证
     *
     * @param userId       用户ID
     * @param credentialId 凭证ID
     */
    void deactivateCredential(UUID userId, String credentialId);

    /**
     * 删除凭证
     *
     * @param userId       用户ID
     * @param credentialId 凭证ID
     */
    void deleteCredential(UUID userId, String credentialId);

    /**
     * 检查凭证健康状态
     * <p>
     * 检测项：
     * - 签名计数器异常（可能的克隆攻击）
     * - 长期未使用的凭证
     * - 异常认证模式
     *
     * @param userId 用户ID
     * @return 异常凭证列表
     */
    List<WebauthnCredentialDTO> checkCredentialHealth(UUID userId);

    /**
     * 记录认证尝试
     *
     * @param userId       用户ID
     * @param credentialId 凭证ID
     * @param success      是否成功
     * @param ipAddress    IP地址
     * @param userAgent    用户代理
     */
    void logAuthenticationAttempt(UUID userId, String credentialId,
                                   boolean success, String ipAddress, String userAgent);
}
