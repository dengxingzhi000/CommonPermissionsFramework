package com.frog.common.redis.lock;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable token describing an acquired lock instance.
 */
public record LockToken(
        String lockKey,
        String redisKey,
        String lockValue,
        String ownerId,
        Instant acquiredAt,
        Duration ttl) {
}
