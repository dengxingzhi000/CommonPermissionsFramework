package com.frog.common.integration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CloudEvents-like envelope to wrap business events with metadata.
 */
@Getter
@Setter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageEnvelope<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this event instance.
     */
    private String id;

    /**
     * Event type, e.g. "auth.user.login".
     */
    private String type;

    /**
     * Event source, e.g. service name or URI.
     */
    private String source;

    /**
     * CloudEvents spec version. Kept as string for compatibility.
     */
    private String specVersion;

    /**
     * Optional subject or logical key.
     */
    private String subject;

    /**
     * Event creation time in UTC.
     */
    private Instant time;

    /**
     * Trace identifier for correlation.
     */
    private String traceId;

    /**
     * Tenant or environment partition if applicable.
     */
    private String tenantId;

    /**
     * Schema/version of the data payload.
     */
    private String version;

    /**
     * Business payload.
     */
    private T data;

    /**
     * Free-form extension attributes.
     */
    @Builder.Default
    private Map<String, String> extensions = new HashMap<>();

    public static <T> MessageEnvelope<T> of(String type, String source, T data) {
        return MessageEnvelope.<T>builder()
                .id(UUID.randomUUID().toString())
                .type(type)
                .source(source)
                .specVersion("1.0")
                .time(Instant.now())
                .data(data)
                .build();
    }
}
