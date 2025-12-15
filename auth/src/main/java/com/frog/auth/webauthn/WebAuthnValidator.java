package com.frog.auth.webauthn;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.validator.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * WebAuthn 验证器
 * 使用 WebAuthn4J 库实现 W3C WebAuthn 标准验证
 *
 * @author Deng
 * @since 2025-12-15
 */
@Component
@Slf4j
public class WebAuthnValidator {
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final AAGUID ZERO_AAGUID = new AAGUID(new byte[16]);

    private final WebAuthnConfig webAuthnConfig;
    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter objectConverter;

    public WebAuthnValidator(WebAuthnConfig webAuthnConfig) {
        this.webAuthnConfig = Objects.requireNonNull(webAuthnConfig, "webAuthnConfig");
        this.objectConverter = new ObjectConverter();
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter);
    }

    /**
     * 验证注册响应
     *
     * @param clientDataJSON    客户端数据 JSON (Base64URL)
     * @param attestationObject 证明对象 (Base64URL)
     * @param expectedChallenge 预期的挑战值
     * @return 验证后的凭证数据
     */
    public RegistrationResult validateRegistration(
            String clientDataJSON,
            String attestationObject,
            String expectedChallenge) {

        try {
            RegistrationData registrationData = parseAndValidateRegistration(
                    clientDataJSON,
                    attestationObject,
                    expectedChallenge
            );
            RegistrationResult result = buildRegistrationResult(registrationData);
            log.debug("WebAuthn registration validated successfully");
            return result;
        } catch (ValidationException e) {
            log.warn("WebAuthn registration validation failed: {}", e.getMessage());
            throw new IllegalStateException("WebAuthn 注册验证失败", e);
        } catch (RuntimeException e) {
            log.error("WebAuthn registration processing error", e);
            throw new IllegalStateException("WebAuthn 注册处理错误", e);
        }
    }

    /**
     * 验证认证响应
     *
     * @param credentialId      凭证ID (Base64URL)
     * @param clientDataJSON    客户端数据 JSON (Base64URL)
     * @param authenticatorData 认证器数据 (Base64URL)
     * @param signature         签名 (Base64URL)
     * @param expectedChallenge 预期的挑战值
     * @param storedPublicKey   存储的公钥 (COSE 格式)
     * @param storedSignCount   存储的签名计数
     * @return 新的签名计数
     */
    public AuthenticationResult validateAuthentication(
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature,
            String expectedChallenge,
            byte[] storedPublicKey,
            long storedSignCount) {

        try {
            AuthenticationData authenticationData = parseAndValidateAuthentication(
                    credentialId,
                    clientDataJSON,
                    authenticatorData,
                    signature,
                    expectedChallenge,
                    storedPublicKey,
                    storedSignCount
            );
            long newSignCount = authenticationData.getAuthenticatorData().getSignCount();
            log.debug("WebAuthn authentication validated successfully, newSignCount={}", newSignCount);
            return new AuthenticationResult(newSignCount, true);
        } catch (ValidationException e) {
            log.warn("WebAuthn authentication validation failed: {}", e.getMessage());
            throw new IllegalStateException("WebAuthn 认证验证失败", e);
        } catch (RuntimeException e) {
            log.error("WebAuthn authentication processing error", e);
            throw new IllegalStateException("WebAuthn 认证处理错误", e);
        }
    }

    /**
     * 将 COSE 公钥序列化为字节数组
     */
    public byte[] serializeCOSEKey(COSEKey coseKey) {
        Objects.requireNonNull(coseKey, "coseKey");
        return objectConverter.getCborConverter().writeValueAsBytes(coseKey);
    }

    private RegistrationData parseAndValidateRegistration(
            String clientDataJSON,
            String attestationObject,
            String expectedChallenge) {
        byte[] clientDataJsonBytes = base64UrlDecode(clientDataJSON, "clientDataJSON");
        byte[] attestationObjectBytes = base64UrlDecode(attestationObject, "attestationObject");

        ServerProperty serverProperty = buildServerProperty(expectedChallenge);
        RegistrationRequest registrationRequest = new RegistrationRequest(attestationObjectBytes, clientDataJsonBytes);
        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty,
                null,
                webAuthnConfig.isUserVerificationRequired()
        );

        RegistrationData registrationData = webAuthnManager.parse(registrationRequest);
        webAuthnManager.validate(registrationData, registrationParameters);
        return registrationData;
    }

    private RegistrationResult buildRegistrationResult(RegistrationData registrationData) {
        AttestationObject attestationObject = registrationData.getAttestationObject();
        AttestedCredentialData credentialData = attestationObject.getAuthenticatorData().getAttestedCredentialData();
        if (credentialData == null) {
            throw new IllegalStateException("No attested credential data found");
        }
        return new RegistrationResult(
                credentialData.getCredentialId(),
                credentialData.getCOSEKey(),
                attestationObject.getAuthenticatorData().getSignCount(),
                credentialData.getAaguid()
        );
    }

    private AuthenticationData parseAndValidateAuthentication(
            String credentialId,
            String clientDataJSON,
            String authenticatorData,
            String signature,
            String expectedChallenge,
            byte[] storedPublicKey,
            long storedSignCount) {
        Objects.requireNonNull(storedPublicKey, "storedPublicKey");

        byte[] credentialIdBytes = base64UrlDecode(credentialId, "credentialId");
        byte[] clientDataJsonBytes = base64UrlDecode(clientDataJSON, "clientDataJSON");
        byte[] authenticatorDataBytes = base64UrlDecode(authenticatorData, "authenticatorData");
        byte[] signatureBytes = base64UrlDecode(signature, "signature");

        ServerProperty serverProperty = buildServerProperty(expectedChallenge);
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                credentialIdBytes,
                null,
                authenticatorDataBytes,
                clientDataJsonBytes,
                null,
                signatureBytes
        );

        COSEKey coseKey = objectConverter.getCborConverter().readValue(storedPublicKey, COSEKey.class);
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
                ZERO_AAGUID,
                credentialIdBytes,
                coseKey
        );
        AuthenticatorImpl authenticatorWithKey = new AuthenticatorImpl(attestedCredentialData, null, storedSignCount);

        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                serverProperty,
                authenticatorWithKey,
                List.of(credentialIdBytes),
                webAuthnConfig.isUserVerificationRequired()
        );

        AuthenticationData authenticationData = webAuthnManager.parse(authenticationRequest);
        webAuthnManager.validate(authenticationData, authenticationParameters);
        return authenticationData;
    }

    private ServerProperty buildServerProperty(String expectedChallenge) {
        String rpOrigin = requireNonBlank(webAuthnConfig.getOrigin(), "webauthn.rp.origin");
        String rpId = requireNonBlank(webAuthnConfig.getId(), "webauthn.rp.id");

        Origin origin = new Origin(rpOrigin);
        Challenge challenge = new DefaultChallenge(base64UrlDecode(expectedChallenge, "expectedChallenge"));
        return new ServerProperty(origin, rpId, challenge, null);
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value;
    }

    private static byte[] base64UrlDecode(String base64Url, String fieldName) {
        if (base64Url == null || base64Url.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        try {
            return BASE64_URL_DECODER.decode(base64Url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " is not valid Base64URL", e);
        }
    }

    /**
     * 注册结果
     */
    public record RegistrationResult(
            byte[] credentialId,
            COSEKey publicKey,
            long signCount,
            AAGUID aaguid
    ) {
        public RegistrationResult {
            credentialId = credentialId == null ? null : credentialId.clone();
        }

        public byte[] credentialId() {
            return credentialId == null ? null : credentialId.clone();
        }

        public String getCredentialIdBase64() {
            return credentialId == null ? null : BASE64_URL_ENCODER.encodeToString(credentialId);
        }

        public String getAaguidString() {
            return aaguid != null ? aaguid.toString() : null;
        }
    }

    /**
     * 认证结果
     */
    public record AuthenticationResult(
            long newSignCount,
            boolean success
    ) {}
}
