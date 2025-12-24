package com.frog.gateway.util;

import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 implementation that includes the canonicalized body for higher tamper resistance.
 * HMAC-SHA256 实现，包括规范化主体以实现更高的防篡改能力。
 */
@Component
public class RequestSignatureCalculator extends AbstractHmacSignatureAlgorithm {
    private static final String VERSION = "HMAC-SHA256-V2";

    @Override
    public String version() {
        return VERSION;
    }
}