package com.frog.gateway.security;

import com.alibaba.fastjson2.JSON;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Signs identity payloads for downstream verification.
 * 对用于下游验证的身份信息载荷进行签名。
 */
public record IdentityTokenEncoder(byte[] secret) {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();

    public IdentityTokenEncoder {
        secret = (secret != null) ? secret : new byte[0];
    }

    public IdentityTokenEncoder(String secret) {
        this(validateSecret(secret));
    }

    private static byte[] validateSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("Identity signature secret must be configured");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(Map<String, Object> payload) {
        String jsonPayload = JSON.toJSONString(payload);
        String encodedPayload = BASE64.encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = sign(encodedPayload.getBytes(StandardCharsets.UTF_8));
        String signature = BASE64.encodeToString(signatureBytes);
        return encodedPayload + "." + signature;
    }

    private byte[] sign(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret(), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign identity payload", ex);
        }
    }
}
