package com.frog.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security headers toggle configuration.
 */
@Component
@ConfigurationProperties(prefix = "frog.security.headers")
@Data
public class SecurityHeadersProperties {

    /** Global switch for header customization. */
    private boolean enabled = true;

    private boolean hstsEnabled = true;
    private long hstsMaxAgeSeconds = 31536000;
    private boolean hstsIncludeSubdomains = true;

    // kept disabled by default to preserve previous behavior; enable to emit X-Content-Type-Options
    private boolean contentTypeOptionsEnabled = false;
    private boolean frameOptionsEnabled = true;
    private boolean referrerPolicyEnabled = true;
    private String referrerPolicy = "STRICT_ORIGIN_WHEN_CROSS_ORIGIN";
}
