package com.frog.common.redis.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock backed by Redis with lease renewal and metrics hooks.
 */
@Component
@Slf4j
public class DistributedLock {
    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofMillis(200);
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);
    private static final Long LUA_SUCCESS = 1L;
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;
    private static final String RENEW_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

    private final RedisTemplate<String, Object> redisTemplate;
    private final LockLeaseManager leaseManager;
    private final LockMetricsRecorder metricsRecorder;
    private final DefaultRedisScript<Long> unlockScript;
    private final DefaultRedisScript<Long> renewScript;
    private final String ownerId;

    public DistributedLock(RedisTemplate<String, Object> redisTemplate,
                           LockLeaseManager leaseManager,
                           LockMetricsRecorder metricsRecorder) {
        this.redisTemplate = redisTemplate;
        this.leaseManager = leaseManager;
        this.metricsRecorder = metricsRecorder;
        this.unlockScript = buildScript(UNLOCK_LUA);
        this.renewScript = buildScript(RENEW_LUA);
        this.ownerId = resolveOwnerId();
    }

    public LockHandle acquire(String lockKey, Duration ttl) {
        return acquire(lockKey, ttl, Duration.ZERO, DEFAULT_RETRY_INTERVAL);
    }

    public LockHandle acquire(String lockKey,
                              Duration ttl,
                              Duration maxWait,
                              Duration retryInterval) {
        Objects.requireNonNull(lockKey, "lockKey");
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        Duration sanitizedRetry = retryInterval == null || retryInterval.isZero()
                ? DEFAULT_RETRY_INTERVAL
                : retryInterval;

        long deadline = maxWait == null || maxWait.isZero() ? 0L
                : System.nanoTime() + maxWait.toNanos();
        LockToken token = tryAcquire(lockKey, ttl);
        if (token != null) {
            return prepareHandle(token);
        }
        if (deadline == 0L) {
            metricsRecorder.recordAcquireFailure(lockKey);
            throw new LockAcquisitionException(lockKey, Duration.ZERO);
        }
        var sample = metricsRecorder.startWaitTimer();
        while (System.nanoTime() < deadline) {
            sleepWithJitter(sanitizedRetry);
            token = tryAcquire(lockKey, ttl);
            if (token != null) {
                metricsRecorder.recordWait(lockKey, sample, true);
                return prepareHandle(token);
            }
        }
        metricsRecorder.recordAcquireFailure(lockKey);
        metricsRecorder.recordWait(lockKey, sample, false);
        throw new LockAcquisitionException(lockKey, maxWait);
    }

    public <T> T executeWithLock(String lockKey,
                                 Duration ttl,
                                 Supplier<T> action) {
        return executeWithLock(lockKey, ttl, DEFAULT_WAIT, DEFAULT_RETRY_INTERVAL, action);
    }

    public <T> T executeWithLock(String lockKey,
                                 Duration ttl,
                                 Duration maxWait,
                                 Duration retryInterval,
                                 Supplier<T> action) {
        try (LockHandle handle = acquire(lockKey, ttl, maxWait, retryInterval)) {
            return action.get();
        }
    }

    public void executeWithLock(String lockKey,
                                Duration ttl,
                                Runnable action) {
        executeWithLock(lockKey, ttl, DEFAULT_WAIT, DEFAULT_RETRY_INTERVAL, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Backwards compatible API: prefer acquire().
     */
    @Deprecated
    public String tryLock(String lockKey, Duration expireTime) {
        LockToken token = tryAcquire(lockKey, expireTime);
        return token == null ? null : token.lockValue();
    }

    /**
     * Backwards compatible API: prefer acquire().
     */
    @Deprecated
    public String tryLockWithRetry(String lockKey,
                                   Duration expireTime,
                                   Duration waitTime,
                                   Duration retryInterval) {
        LockToken token = tryAcquire(lockKey, expireTime);
        if (token != null) {
            return token.lockValue();
        }
        Duration effectiveWait = waitTime == null ? Duration.ZERO : waitTime;
        if (effectiveWait.isZero()) {
            return null;
        }
        long deadline = System.nanoTime() + effectiveWait.toNanos();
        Duration interval = retryInterval == null ? DEFAULT_RETRY_INTERVAL : retryInterval;
        while (System.nanoTime() < deadline) {
            sleepWithJitter(interval);
            token = tryAcquire(lockKey, expireTime);
            if (token != null) {
                return token.lockValue();
            }
        }
        return null;
    }

    /**
     * Backwards compatible API: prefer try-with-resources on LockHandle.
     */
    @Deprecated
    public boolean unlock(String lockKey, String lockId) {
        LockToken token = new LockToken(lockKey, namespaced(lockKey), lockId, ownerId, Instant.now(), Duration.ZERO);
        leaseManager.cancel(token);
        Long result = redisTemplate.execute(
                unlockScript,
                Collections.singletonList(token.redisKey()),
                token.lockValue()
        );
        boolean success = LUA_SUCCESS.equals(result);
        if (!success) {
            metricsRecorder.recordReleaseFailure(lockKey);
        }
        return success;
    }

    public boolean isLocked(String lockKey) {
        return redisTemplate.hasKey(namespaced(lockKey));
    }

    public boolean renewLock(String lockKey, String lockId, Duration ttl) {
        LockToken token = new LockToken(lockKey, namespaced(lockKey), lockId, ownerId, Instant.now(), ttl);
        return renewLease(token);
    }

    void releaseInternal(LockToken token) {
        leaseManager.cancel(token);
        Long result = redisTemplate.execute(
                unlockScript,
                Collections.singletonList(token.redisKey()),
                token.lockValue()
        );
        if (!LUA_SUCCESS.equals(result)) {
            metricsRecorder.recordReleaseFailure(token.lockKey());
            throw new LockReleaseException(token.redisKey());
        }
    }

    private LockHandle prepareHandle(LockToken token) {
        leaseManager.register(token, () -> {
            if (!renewLease(token)) {
                metricsRecorder.recordRenewFailure(token.lockKey());
                leaseManager.cancel(token);
            }
        });
        metricsRecorder.recordAcquireSuccess(token.lockKey());
        return new LockHandle(this, token);
    }

    private LockToken tryAcquire(String lockKey, Duration ttl) {
        String redisKey = namespaced(lockKey);
        String lockValue = ownerId + ":" + UUID.randomUUID();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, lockValue, ttl);
        if (Boolean.TRUE.equals(success)) {
            return new LockToken(lockKey, redisKey, lockValue, ownerId, Instant.now(), ttl);
        }
        return null;
    }

    private boolean renewLease(LockToken token) {
        Long result = redisTemplate.execute(
                renewScript,
                Collections.singletonList(token.redisKey()),
                token.lockValue(),
                String.valueOf(token.ttl().toMillis())
        );
        return LUA_SUCCESS.equals(result);
    }

    private static DefaultRedisScript<Long> buildScript(String script) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    private static String resolveOwnerId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = "instance";
        }
        return hostname + "-" + UUID.randomUUID();
    }

    private static void sleepWithJitter(Duration baseInterval) {
        long baseMillis = Math.max(1L, baseInterval.toMillis());
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, baseMillis / 4));
        try {
            TimeUnit.MILLISECONDS.sleep(baseMillis + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String namespaced(String lockKey) {
        return LOCK_PREFIX + lockKey;
    }
}
