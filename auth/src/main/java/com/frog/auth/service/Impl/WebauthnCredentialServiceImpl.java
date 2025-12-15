package com.frog.auth.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.frog.auth.domain.dto.*;
import com.frog.auth.domain.entity.WebauthnCredential;
import com.frog.auth.mapper.WebauthnCredentialMapper;
import com.frog.auth.service.IWebauthnCredentialService;
import com.frog.auth.webauthn.WebAuthnValidator;
import com.frog.common.dto.auth.*;
import com.frog.common.feign.client.SysUserServiceClient;
import com.frog.common.security.properties.JwtProperties;
import com.frog.common.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebauthnCredentialServiceImpl extends ServiceImpl<WebauthnCredentialMapper, WebauthnCredential>
        implements IWebauthnCredentialService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final SysUserServiceClient userServiceClient;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final WebauthnCredentialMapper credentialMapper;
    private final WebauthnCredentialConverter credentialConverter;
    private final WebAuthnValidator webAuthnValidator;

    private static final String WA_CHALLENGE_PREFIX = "webauthn:challenge:";
    private static final String WA_REG_CHALLENGE_PREFIX = "webauthn:reg:challenge:";
    private static final String WA_CREDENTIAL_PREFIX = "webauthn:cred:";
    private static final String WA_AUTH_ATTEMPT_PREFIX = "webauthn:auth:attempt:";
    private static final int WEBAUTHN_CHALLENGE_BYTE_LENGTH = 32;
    private static final long CHALLENGE_EXPIRY_SECONDS = 120L;
    private static final long REG_CHALLENGE_EXPIRY_SECONDS = 300L;
    private static final long CREDENTIAL_INACTIVE_DAYS = 90L;

    @Override
    public WebAuthnRegisterChallengeResponse generateRegistrationChallenge(
            UUID userId, String username, String deviceId, String rpId) {
        log.info("Generating registration challenge for user={}, device={}", userId, deviceId);

        String challenge = base64Url(randomBytes());
        String key = WA_REG_CHALLENGE_PREFIX + userId + ":" + deviceId;
        redisTemplate.opsForValue().set(key, challenge, REG_CHALLENGE_EXPIRY_SECONDS, TimeUnit.SECONDS);

        return WebAuthnRegisterChallengeResponse.builder()
                .challenge(challenge)
                .rpId(rpId)
                .timeout(REG_CHALLENGE_EXPIRY_SECONDS * 1000)
                .user(Map.of(
                        "id", userId,
                        "name", username,
                        "displayName", username
                ))
                .attestation("none")
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebauthnCredentialDTO registerCredential(UUID userId, WebauthnRegistrationRequest request) {
        log.info("Registering WebAuthn credential for user={}, credentialId={}", userId, request.getCredentialId());

        // 检查凭证是否已存在
        WebauthnCredential existing = credentialMapper.findByUserIdAndCredId(userId, request.getCredentialId());
        if (existing != null) {
            throw new IllegalStateException("凭证ID已存在");
        }

        // 获取并验证挑战
        String challengeKey = WA_REG_CHALLENGE_PREFIX + userId + ":" + request.getDeviceId();
        Object expectedChallenge = redisTemplate.opsForValue().get(challengeKey);
        if (expectedChallenge == null) {
            throw new IllegalStateException("注册挑战已过期或不存在");
        }

        // 使用 WebAuthn4J 验证注册响应
        WebAuthnValidator.RegistrationResult validationResult = webAuthnValidator.validateRegistration(
                request.getClientDataJSON(),
                request.getAttestationObject(),
                expectedChallenge.toString()
        );

        // 删除已使用的挑战
        redisTemplate.delete(challengeKey);

        // 构建并保存凭证
        WebauthnCredential credential = WebauthnCredential.builder()
                .id(UUID.randomUUID())
                .credentialId(validationResult.getCredentialIdBase64())
                .userId(userId)
                .publicKeyPem(Base64.getEncoder().encodeToString(
                        webAuthnValidator.serializeCOSEKey(validationResult.publicKey())))
                .alg(getAlgorithmName(validationResult.publicKey()))
                .signCount(validationResult.signCount())
                .deviceName(request.getDeviceName())
                .aaguid(validationResult.getAaguidString())
                .transports(request.getTransports())
                .isActive(true)
                .createdTime(LocalDateTime.now())
                .updatedTime(LocalDateTime.now())
                .build();

        credentialMapper.insert(credential);

        log.info("Successfully registered WebAuthn credential for user={}, credentialId={}",
                userId, credential.getCredentialId());
        return credentialConverter.toDTO(credential);
    }

    /**
     * 获取 COSE 公钥算法名称
     */
    private String getAlgorithmName(com.webauthn4j.data.attestation.authenticator.COSEKey coseKey) {
        if (coseKey == null || coseKey.getAlgorithm() == null) {
            return "ES256"; // 默认
        }
        return switch (coseKey.getAlgorithm().getValue().intValue()) {
            case -7 -> "ES256";
            case -35 -> "ES384";
            case -36 -> "ES512";
            case -257 -> "RS256";
            case -258 -> "RS384";
            case -259 -> "RS512";
            case -8 -> "EdDSA";
            default -> "UNKNOWN";
        };
    }

    @Override
    public WebAuthnChallengeResponse generateAuthenticationChallenge(
            UUID userId, String username, String deviceId, String rpId) {
        log.info("Generating authentication challenge for user={}, device={}", userId, deviceId);

        String challenge = base64Url(randomBytes());
        String key = WA_CHALLENGE_PREFIX + userId + ":" + deviceId;
        redisTemplate.opsForValue().set(key, challenge, CHALLENGE_EXPIRY_SECONDS, TimeUnit.SECONDS);

        // 获取用户所有活跃凭证
        List<WebauthnCredential> creds = credentialMapper.findByUserId(userId);
        List<Map<String, Object>> allowCredentials = creds.stream()
                .map(c -> {
                    Map<String, Object> cred = new HashMap<>();
                    cred.put("id", c.getCredentialId());
                    cred.put("type", "public-key");
                    if (c.getTransports() != null) {
                        cred.put("transports", Arrays.asList(c.getTransports().split(",")));
                    }
                    return cred;
                })
                .collect(Collectors.toList());

        return WebAuthnChallengeResponse.builder()
                .challenge(challenge)
                .rpId(rpId)
                .timeout(CHALLENGE_EXPIRY_SECONDS * 1000)
                .allowCredentials(allowCredentials)
                .userVerification("preferred")
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenUpgradeResponse authenticateAndUpgradeToken(
            UUID userId, String username, WebauthnAuthenticationRequest request,
            String deviceId, String ipAddress) {
        log.info("Authenticating WebAuthn for user={}, credentialId={}", userId, request.getCredentialId());

        // 验证挑战
        String key = WA_CHALLENGE_PREFIX + userId + ":" + deviceId;
        Object expectedChallenge = redisTemplate.opsForValue().get(key);
        if (expectedChallenge == null) {
            throw new IllegalStateException("WebAuthn挑战已过期或不存在");
        }

        // 获取凭证
        WebauthnCredential credential = credentialMapper.findByUserIdAndCredId(userId, request.getCredentialId());
        if (credential == null || !credential.isAvailable()) {
            throw new IllegalStateException("凭证不存在或已停用");
        }

        // 验证签名计数器（防克隆攻击）- 预检查
        if (!credential.isCounterValid(request.getSignCount())) {
            log.warn("Invalid signature counter for user={}, credentialId={}, expected>{}, got={}",
                    userId, request.getCredentialId(), credential.getSignCount(), request.getSignCount());
            throw new IllegalStateException("签名计数器异常，可能存在克隆攻击");
        }

        // 使用 WebAuthn4J 验证断言签名
        byte[] storedPublicKey = Base64.getDecoder().decode(credential.getPublicKeyPem());
        WebAuthnValidator.AuthenticationResult authResult = webAuthnValidator.validateAuthentication(
                request.getCredentialId(),
                request.getClientDataJSON(),
                request.getAuthenticatorData(),
                request.getSignature(),
                expectedChallenge.toString(),
                storedPublicKey,
                credential.getSignCount()
        );

        // 更新凭证使用信息（使用验证后返回的新签名计数）
        credentialMapper.updateSignCount(userId, request.getCredentialId(), authResult.newSignCount());

        // 删除已使用的挑战
        redisTemplate.delete(key);

        // 获取用户权限
        Set<String> roles = userServiceClient.findRolesByUserId(userId).data();
        Set<String> permissions = userServiceClient.findPermissionsByUserId(userId).data();

        // 签发带AMR的访问令牌
        List<String> amr = Arrays.asList("pwd", "webauthn");
        String accessToken = jwtUtils.generateAccessToken(
                userId, username, roles, permissions, deviceId, ipAddress, amr);

        log.info("Successfully authenticated user={} with WebAuthn", userId);

        return TokenUpgradeResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration() / 1000)
                .build();
    }

    @Override
    public List<WebauthnCredentialDTO> listActiveCredentials(UUID userId) {
        log.debug("Listing active credentials for user={}", userId);
        List<WebauthnCredential> credentials = credentialMapper.listActiveCredentials(userId);
        return credentialConverter.toDTOList(credentials);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WebauthnCredentialDTO updateDeviceName(UUID userId, String credentialId, String deviceName) {
        log.info("Updating device name for user={}, credentialId={}", userId, credentialId);

        int updated = credentialMapper.updateDeviceName(userId, credentialId, deviceName);
        if (updated == 0) {
            throw new IllegalStateException("凭证不存在或更新失败");
        }

        WebauthnCredential credential = credentialMapper.findByUserIdAndCredId(userId, credentialId);
        return credentialConverter.toDTO(credential);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deactivateCredential(UUID userId, String credentialId) {
        log.info("Deactivating credential for user={}, credentialId={}", userId, credentialId);

        int updated = credentialMapper.disableCredential(userId, credentialId);
        if (updated == 0) {
            throw new IllegalStateException("凭证不存在或停用失败");
        }

        // 清理Redis缓存
        String cacheKey = WA_CREDENTIAL_PREFIX + userId + ":" + credentialId;
        redisTemplate.delete(cacheKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCredential(UUID userId, String credentialId) {
        log.info("Deleting credential for user={}, credentialId={}", userId, credentialId);

        int deleted = credentialMapper.deleteByUserIdAndCredId(userId, credentialId);
        if (deleted == 0) {
            throw new IllegalStateException("凭证不存在或删除失败");
        }

        // 清理Redis缓存
        String cacheKey = WA_CREDENTIAL_PREFIX + userId + ":" + credentialId;
        redisTemplate.delete(cacheKey);
    }

    @Override
    public List<WebauthnCredentialDTO> checkCredentialHealth(UUID userId) {
        log.debug("Checking credential health for user={}", userId);

        List<WebauthnCredential> credentials = credentialMapper.findByUserId(userId);
        List<WebauthnCredential> unhealthy = new ArrayList<>();

        LocalDateTime inactiveThreshold = LocalDateTime.now().minusDays(CREDENTIAL_INACTIVE_DAYS);

        for (WebauthnCredential credential : credentials) {
            boolean isUnhealthy = false;

            // 检查长期未使用
            if (credential.getLastUsedAt() != null &&
                credential.getLastUsedAt().isBefore(inactiveThreshold)) {
                log.warn("Credential {} for user {} has been inactive for over {} days",
                        credential.getCredentialId(), userId, CREDENTIAL_INACTIVE_DAYS);
                isUnhealthy = true;
            }

            // TODO: 添加更多健康检查
            // - 签名计数器异常检测
            // - 异常认证模式检测

            if (isUnhealthy) {
                unhealthy.add(credential);
            }
        }

        return credentialConverter.toDTOList(unhealthy);
    }

    @Override
    public void logAuthenticationAttempt(UUID userId, String credentialId,
                                          boolean success, String ipAddress, String userAgent) {
        String key = WA_AUTH_ATTEMPT_PREFIX + userId + ":" + credentialId + ":" + System.currentTimeMillis();
        Map<String, Object> attempt = new HashMap<>();
        attempt.put("success", success);
        attempt.put("ipAddress", ipAddress);
        attempt.put("userAgent", userAgent);
        attempt.put("timestamp", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(key, attempt);
        redisTemplate.expire(key, 30, TimeUnit.DAYS);

        if (!success) {
            log.warn("Failed WebAuthn authentication attempt for user={}, credentialId={}, ip={}",
                    userId, credentialId, ipAddress);
        }
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[WEBAUTHN_CHALLENGE_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
