package com.frog.system.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for notification subsystem (retry policy, etc.).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "system.notification")
public class NotificationProperties {
    private Retry retry = new Retry();

    @Getter
    @Setter
    public static class Retry {
        /**
         * Maximum retry attempts per channel.
         */
        private int maxAttempts = 3;

        /**
         * Backoff in milliseconds per attempt.
         */
        private List<Long> backoff = new ArrayList<>(List.of(200L, 500L, 1000L));
    }
}

