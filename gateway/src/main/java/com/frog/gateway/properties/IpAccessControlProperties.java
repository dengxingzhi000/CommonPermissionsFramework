package com.frog.gateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * External configuration for {@link com.frog.gateway.filter.IpAccessControlFilter}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.ip-access")
public class IpAccessControlProperties {

    /**
     * Toggle for the whole filter.
     */
    private boolean enabled = true;

    /**
     * When enabled the gateway only accepts IPs that are explicitly whitelisted.
     */
    private boolean whitelistOnly = false;

    /**
     * Max cache entries for decisions.
     */
    private long cacheMaxSize = 20000;

    /**
     * TTL for cached allow/deny decisions.
     */
    private Duration cacheTtl = Duration.ofMinutes(1);

    /**
     * Response body message for blocked calls.
     */
    private String blockMessage = "Request rejected by IP access control";

    /**
     * Headers inspected for the client IP when the immediate peer is trusted.
     */
    private List<String> forwardedHeaders = List.of("X-Forwarded-For", "X-Real-IP");

    /**
     * List of trusted proxy addresses or CIDR blocks.
     */
    private List<String> trustedProxies = new ArrayList<>();
}
