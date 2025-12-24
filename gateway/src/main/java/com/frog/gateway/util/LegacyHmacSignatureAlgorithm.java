package com.frog.gateway.util;

import org.springframework.stereotype.Component;

/**
 * Backward compatible HMAC-SHA256 implementation (V1) for signature verification.
 */
@Component
public class LegacyHmacSignatureAlgorithm extends AbstractHmacSignatureAlgorithm {
    private static final String VERSION = "HMAC-SHA256-V1";

    @Override
    public String version() {
        return VERSION;
    }
}