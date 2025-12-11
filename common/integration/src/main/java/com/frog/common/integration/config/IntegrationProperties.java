package com.frog.common.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "integration.messaging")
public class IntegrationProperties {
    /**
     * Enable confirm callbacks and return callbacks for publishers.
     */
    private boolean publisherConfirms = true;

    /**
     * When true, add x-delay header for delayed messages (requires delayed-message plugin).
     * If false, caller should provide TTL+DLX queues.
     */
    private boolean delayedExchangeEnabled = true;

    /**
     * Default confirm timeout for sync publish.
     */
    private Duration confirmTimeout = Duration.ofSeconds(5);

    /**
     * Default partition count when building ordered routing keys.
     */
    private int orderingPartitions = 4;
}
