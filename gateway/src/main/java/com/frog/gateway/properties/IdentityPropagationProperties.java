package com.frog.gateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for propagating authenticated identity to downstream services.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.identity")
public class IdentityPropagationProperties {

    /**
     * Enable downstream identity propagation.
     */
    private boolean enabled = true;

    /**
     * Header carrying the signed identity payload.
     */
    private String identityTokenHeader = "X-Identity-Token";

    private String userIdHeader = "X-User-Id";
    private String usernameHeader = "X-User-Name";
    private String deviceIdHeader = "X-Device-Id";
    private String rolesHeader = "X-User-Roles";

    /**
     * Claims names read from the JWT.
     */
    private String userIdClaim = "userId";
    private String usernameClaim = "username";
    private String deviceIdClaim = "deviceId";

    /**
     * Shared secret for signing identity headers. Should be rotated regularly.
     */
    private String signatureSecret = "changeit-identity-secret";
}
