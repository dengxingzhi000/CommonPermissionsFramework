package com.frog.common.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "integration.kafka")
public class KafkaIntegrationProperties {
    /**
    * Bootstrap servers, comma separated.
    */
    private String bootstrapServers = "localhost:9092";
    /**
     * Producer client id.
     */
    private String clientId = "integration-producer";
    /**
     * Required acks (all is recommended).
     */
    private String acks = "all";
    /**
     * Retries for send.
     */
    private int retries = 3;
    /**
     * Linger ms to batch.
     */
    private int lingerMs = 5;
    /**
     * Batch size bytes.
     */
    private int batchSize = 16384;
    /**
     * Max in flight requests per connection.
     */
    private int maxInFlight = 5;
    /**
     * Enable idempotence.
     */
    private boolean idempotence = true;

    /**
     * Enable DLQ publish on failure after retries.
     */
    private boolean dlqEnabled = true;

    /**
     * Suffix appended to original topic for DLQ (e.g., topic + ".dlq").
     */
    private String dlqSuffix = ".dlq";

    /**
     * Max attempts including the first try.
     */
    private int maxAttempts = 3;

    /**
     * Retry backoff initial interval.
     */
    private Duration backoffInitial = Duration.ofMillis(200);

    /**
     * Retry backoff max interval.
     */
    private Duration backoffMax = Duration.ofSeconds(5);

    /**
     * Retry backoff multiplier.
     */
    private double backoffMultiplier = 2.0;
}
