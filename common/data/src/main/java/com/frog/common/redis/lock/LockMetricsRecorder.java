package com.frog.common.redis.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class LockMetricsRecorder {
    private final MeterRegistry meterRegistry;

    LockMetricsRecorder(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry.getIfAvailable();
    }

    void recordAcquireSuccess(String lockKey) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("distributed.lock.acquire.success", "lockKey", lockKey).increment();
    }

    void recordAcquireFailure(String lockKey) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("distributed.lock.acquire.failure", "lockKey", lockKey).increment();
    }

    void recordWait(String lockKey, Timer.Sample sample, boolean success) {
        if (meterRegistry == null || sample == null) {
            return;
        }
        sample.stop(Timer.builder("distributed.lock.wait")
                .tag("lockKey", lockKey)
                .tag("outcome", success ? "success" : "timeout")
                .register(meterRegistry));
    }

    void recordReleaseFailure(String lockKey) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("distributed.lock.release.failure", "lockKey", lockKey).increment();
    }

    void recordRenewFailure(String lockKey) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter("distributed.lock.renew.failure", "lockKey", lockKey).increment();
    }

    Timer.Sample startWaitTimer() {
        return meterRegistry == null ? null : Timer.start(meterRegistry);
    }
}
