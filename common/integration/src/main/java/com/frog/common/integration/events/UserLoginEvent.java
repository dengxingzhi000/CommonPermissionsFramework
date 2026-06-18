package com.frog.common.integration.events;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserLoginEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private UUID userId;
    private String username;
    private String ipAddress;
    private String deviceId;
    private Instant loginTime;
    private String location;
}
