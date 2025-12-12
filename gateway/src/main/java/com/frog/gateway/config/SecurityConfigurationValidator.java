package com.frog.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Security Configuration Validator
 * Ensures all critical security configurations are properly set before application starts.
 * Follows Google/Netflix best practice: fail-fast on missing security configuration.
 */
@Slf4j
@Component
public class SecurityConfigurationValidator {

    @Value("${security.signature.app-secrets.web-app:}")
    private String webAppSecret;

    @Value("${security.signature.app-secrets.internal-service:}")
    private String internalServiceSecret;

    @Value("${security.identity.signature-secret:}")
    private String identitySignatureSecret;

    @Value("${security.mtls.keystore-password:}")
    private String keystorePassword;

    @Value("${security.mtls.truststore-password:}")
    private String truststorePassword;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * Validates all critical security configurations on startup.
     * Application will fail to start if any required configuration is missing in production.
     */
    @PostConstruct
    public void validateSecurityConfiguration() {
        List<String> missingConfigs = new ArrayList<>();

        boolean isProductionLike = isProductionLikeEnvironment();

        log.info("Validating security configuration (profile: {}, strict mode: {})",
                activeProfile, isProductionLike);

        if (!StringUtils.hasText(webAppSecret)) {
            String error = "security.signature.app-secrets.web-app (env: API_SECRET_WEB_APP)";
            missingConfigs.add(error);
        }

        if (!StringUtils.hasText(internalServiceSecret)) {
            String error = "security.signature.app-secrets.internal-service (env: API_SECRET_INTERNAL_SERVICE)";
            missingConfigs.add(error);
        }

        if (!StringUtils.hasText(identitySignatureSecret)) {
            String error = "security.identity.signature-secret (env: IDENTITY_SIGNATURE_SECRET)";
            missingConfigs.add(error);
        }

        if (!StringUtils.hasText(keystorePassword)) {
            String error = "security.mtls.keystore-password (env: KEYSTORE_PASSWORD)";
            missingConfigs.add(error);
        }

        if (!StringUtils.hasText(truststorePassword)) {
            String error = "security.mtls.truststore-password (env: TRUSTSTORE_PASSWORD)";
            missingConfigs.add(error);
        }

        if (!missingConfigs.isEmpty()) {
            String errorMessage = buildErrorMessage(missingConfigs);

            if (isProductionLike) {
                log.error("CRITICAL SECURITY ERROR: {}", errorMessage);
                throw new IllegalStateException(errorMessage);
            } else {
                log.warn("\n\n" +
                        "================================================================================\n" +
                        "  SECURITY WARNING: Missing configuration (acceptable in dev mode)\n" +
                        "  {}\n" +
                        "  These MUST be set via environment variables in production!\n" +
                        "================================================================================\n",
                        errorMessage);
            }
        } else {
            log.info("✓ All critical security configurations are properly set");

            if (isProductionLike) {
                validateSecretStrength();
            }
        }
    }

    /**
     * Validates that secrets meet minimum strength requirements.
     */
    private void validateSecretStrength() {
        List<String> weakSecrets = new ArrayList<>();

        if (webAppSecret.length() < 32) {
            weakSecrets.add("web-app secret is too short (minimum 32 characters)");
        }

        if (internalServiceSecret.length() < 32) {
            weakSecrets.add("internal-service secret is too short (minimum 32 characters)");
        }

        if (identitySignatureSecret.length() < 64) {
            weakSecrets.add("identity signature secret is too short (minimum 64 characters for HMAC-SHA256)");
        }

        if (!weakSecrets.isEmpty()) {
            log.warn("SECURITY WARNING: Weak secrets detected:\n  - {}",
                    String.join("\n  - ", weakSecrets));
        }
    }

    /**
     * Determines if current environment requires strict security validation.
     */
    private boolean isProductionLikeEnvironment() {
        return activeProfile != null &&
                (activeProfile.contains("prod") ||
                        activeProfile.contains("production") ||
                        activeProfile.contains("staging") ||
                        activeProfile.contains("uat"));
    }

    /**
     * Builds detailed error message for missing configurations.
     */
    private String buildErrorMessage(List<String> missingConfigs) {
        return String.format(
                "Missing %d required security configuration(s):\n  - %s\n\n" +
                        "SOLUTION: Set these via environment variables:\n" +
                        "  export API_SECRET_WEB_APP='your-secret-here'\n" +
                        "  export API_SECRET_INTERNAL_SERVICE='your-secret-here'\n" +
                        "  export IDENTITY_SIGNATURE_SECRET='your-secret-here'\n" +
                        "  export KEYSTORE_PASSWORD='your-password-here'\n" +
                        "  export TRUSTSTORE_PASSWORD='your-password-here'\n\n" +
                        "For production deployment, use HashiCorp Vault, AWS Secrets Manager, or equivalent.",
                missingConfigs.size(),
                String.join("\n  - ", missingConfigs)
        );
    }
}
