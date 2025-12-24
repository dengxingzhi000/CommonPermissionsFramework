package com.frog.gateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * External configuration for {@link com.frog.gateway.filter.ApiSignatureFilter}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.signature")
public class ApiSignatureProperties {

    /**
     * Toggle for the signature filter.
     */
    private boolean enabled = true;

    /**
     * Whitelisted paths that bypass signature verification (Ant style).
     */
    private List<String> whitelist = List.of("/public/**", "/actuator/**");

    /**
     * Allowed clock skew window for request timestamps.
     */
    private Duration allowedClockSkew = Duration.ofMinutes(5);

    /**
     * TTL for replay protection nonce.
     */
    private Duration nonceTtl = Duration.ofMinutes(5);

    /**
     * Redis key prefix for nonce entries.
     */
    private String nonceKeyPrefix = "api:nonce:";

    /**
     * Default algorithm version if header is missing.
     */
    private String defaultVersion = "HMAC-SHA256-V2";

    /**
     * AppId -> secret key mapping. Should be injected from a config center.
     */
    private Map<String, String> appSecrets = new HashMap<>();
}
