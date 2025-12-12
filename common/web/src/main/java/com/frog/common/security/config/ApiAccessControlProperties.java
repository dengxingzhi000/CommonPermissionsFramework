package com.frog.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "frog.security.api-filter")
@Data
public class ApiAccessControlProperties {

    private boolean enabled = true;
    private List<String> whitelist = new ArrayList<>(List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/doc.html",
            "/actuator/health"
    ));
    private List<String> bypassPaths = new ArrayList<>();
    private List<String> bypassRoles = new ArrayList<>();
    private List<String> bypassPermissions = new ArrayList<>();
    private List<String> bypassUsers = new ArrayList<>();
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    @Data
    public static class CircuitBreakerProperties {
        private boolean enabled = true;
        private int failureThreshold = 3;
        private Duration openDuration = Duration.ofSeconds(60);
        private boolean bypassOnOpen = true;
        private boolean forceOpen = false;
    }
}
