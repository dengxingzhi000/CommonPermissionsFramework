package com.frog.system.notification.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Command object describing a notification request.
 */
@Getter
@Builder
public class NotificationCommand {
    /**
     * Business identifier for correlation and tracing.
     */
    private final String referenceId;

    /**
     * Optional template code to support centralized template rendering in the future.
     */
    private final String templateCode;

    /**
     * Notification subject/title where applicable.
     */
    private final String subject;

    /**
     * Plain text content (already rendered if templates are not supported).
     */
    private final String content;

    /**
     * Username for in-app/system messaging.
     */
    private final String username;

    /**
     * Email recipient.
     */
    private final String email;

    /**
     * Optional variables for template rendering.
     */
    @Singular
    private final Map<String, Object> variables;

    /**
     * Target channels.
     */
    @Singular
    private final Set<NotificationChannel> channels;

    /**
     * Creation timestamp used for tracing/debugging.
     */
    @Builder.Default
    private final Instant createdAt = Instant.now();

    public boolean hasChannel(NotificationChannel channel) {
        return channels != null && channels.contains(channel);
    }
}
