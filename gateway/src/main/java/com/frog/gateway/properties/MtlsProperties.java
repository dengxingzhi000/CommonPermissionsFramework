package com.frog.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author Deng
 * createData 2025/11/10 11:07
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "security.mtls")
@Data
public class MtlsProperties {
    private Resource keystorePath;
    private String keystorePassword;
    private Resource truststorePath;
    private String truststorePassword;

    // Enable/disable mTLS client config
    private boolean enabled = true;

    // OkHttp timeouts (ms)
    private Integer connectTimeoutMs = 5000;
    private Integer readTimeoutMs = 10000;
    private Integer writeTimeoutMs = 10000;

    // OkHttp connection pool
    private Integer maxIdleConnections = 50;
    private Long keepAliveMinutes = 5L;
}
