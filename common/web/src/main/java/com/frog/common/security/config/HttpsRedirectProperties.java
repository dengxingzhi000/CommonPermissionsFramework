package com.frog.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "frog.security.https-redirect")
@Data
public class HttpsRedirectProperties {
    private boolean enabled = true;
    private int httpPort = 8080;
    private int redirectPort = 8443;
}

