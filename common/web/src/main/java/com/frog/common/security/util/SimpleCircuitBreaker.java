package com.frog.common.security.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal circuit breaker to fail open after repeated errors.
 */
public class SimpleCircuitBreaker {
    private final boolean enabled;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openUntil;

    public SimpleCircuitBreaker(boolean enabled, int failureThreshold, Duration openDuration) {
        this(enabled, failureThreshold, openDuration, Clock.systemUTC());
    }

    public SimpleCircuitBreaker(boolean enabled, int failureThreshold, Duration openDuration, Clock clock) {
        this.enabled = enabled;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = Objects.requireNonNullElse(openDuration, Duration.ofSeconds(30));
        this.clock = Objects.requireNonNullElse(clock, Clock.systemUTC());
    }

    public boolean isOpen(boolean forceOpen) {
        if (!enabled) {
            return false;
        }
        if (forceOpen) {
            return true;
        }
        Instant until = openUntil;
        return until != null && until.isAfter(clock.instant());
    }

    public void recordFailure() {
        if (!enabled) {
            return;
        }
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntil = clock.instant().plus(openDuration);
            consecutiveFailures.set(0);
        }
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntil = null;
    }

    public Duration remainingOpenDuration() {
        Instant until = openUntil;
        if (until == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        if (until.isBefore(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, until);
    }
}
