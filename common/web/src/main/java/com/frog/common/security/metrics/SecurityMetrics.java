package com.frog.common.security.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralized security metrics counters.
 */
@Component
@RequiredArgsConstructor
public class SecurityMetrics {
    private final MeterRegistry meterRegistry;

    public void increment(String name) {
        meterRegistry.counter(name).increment();
    }
}
