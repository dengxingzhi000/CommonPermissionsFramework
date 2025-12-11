package com.frog.common.redis.lock;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background lease renewal for active locks.
 */
@Component
@Slf4j
public class LockLeaseManager {
    private static final long MIN_RENEW_INTERVAL_MILLIS = 500L;

    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> renewTasks = new ConcurrentHashMap<>();

    LockLeaseManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new LeaseThreadFactory());
    }

    void register(LockToken token, Runnable renewalAction) {
        cancel(token);
        Duration ttl = token.ttl();
        long interval = Math.max(ttl.toMillis() / 2, MIN_RENEW_INTERVAL_MILLIS);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                renewalAction.run();
            } catch (Exception ex) {
                log.warn("Lease renewal task failed for key={}", token.redisKey(), ex);
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        renewTasks.put(token.lockValue(), future);
    }

    void cancel(LockToken token) {
        ScheduledFuture<?> future = renewTasks.remove(token.lockValue());
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private static class LeaseThreadFactory implements ThreadFactory {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("lock-lease-" + COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
