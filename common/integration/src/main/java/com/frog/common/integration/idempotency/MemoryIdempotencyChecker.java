package com.frog.common.integration.idempotency;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-memory checker for tests/dev. For production use a distributed store (Redis/DB).
 */
public class MemoryIdempotencyChecker implements IdempotencyChecker {
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public MemoryIdempotencyChecker(Duration ttl) {
        this.ttlMillis = ttl.toMillis();
    }

    @Override
    public boolean tryAcquire(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return true; // cannot dedupe without id
        }
        long now = System.currentTimeMillis();
        purgeExpired(now);
        return seen.putIfAbsent(messageId, now + ttlMillis) == null;
    }

    private void purgeExpired(long now) {
        seen.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
