package com.frog.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 多级缓存策略
 * L1: Caffeine本地缓存
 * L2: Redis分布式缓存
 *
 * @author Deng
 * createData 2025/10/24 14:10
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class MultiLevelCache {
    private final RedisTemplate<String, Object> redisTemplate;

    // 默认TTL（用于从L2回填至L1时）
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String INVALIDATION_CHANNEL = "cache:invalidation";

    private record CacheValue(Object value, long expireAtNanos) {}

    private final Cache<@NonNull String, CacheValue> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfter(new Expiry<@NonNull String, @NonNull CacheValue>() {
                @Override
                public long expireAfterCreate(String key, CacheValue value, long currentTime) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterUpdate(String key, CacheValue value, long currentTime, long currentDuration) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }

                @Override
                public long expireAfterRead(String key, CacheValue value, long currentTime, long currentDuration) {
                    return Math.max(0, value.expireAtNanos() - currentTime);
                }
            })
            .recordStats()
            .build();

    /**
     * 获取缓存
     */
    public <T> T get(String key, Class<T> type) {
        // 1) L1
        CacheValue cv = localCache.getIfPresent(key);
        if (cv != null) {
            return type.cast(cv.value);
        }

        // 2) L2
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            // 使用默认TTL回填L1
            putLocal(key, value, DEFAULT_TTL);
            return type.cast(value);
        }

        return null;
    }

    /**
     * 设置缓存
     */
    public void set(String key, Object value, Duration ttl) {
        // 同时写入L1和L2
        putLocal(key, value, ttl);
        redisTemplate.opsForValue().set(key, value, ttl);
        // 通知其他实例失效本地L1
        try {
            redisTemplate.convertAndSend(INVALIDATION_CHANNEL, key);
        } catch (Exception ignored) {
        }
    }

    /**
     * 删除缓存
     */
    public void evict(String key) {
        localCache.invalidate(key);
        redisTemplate.delete(key);
        // 通知其他实例失效本地L1
        try {
            redisTemplate.convertAndSend(INVALIDATION_CHANNEL, key);
        } catch (Exception ignored) {
        }
    }

    /**
     * 获取或加载（带回源与写穿）
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T val = get(key, type);
        if (val != null) {
            return val;
        }

        T loaded = Objects.requireNonNull(loader.get(), "loader returned null");
        set(key, loaded, ttl != null ? ttl : DEFAULT_TTL);
        return loaded;
    }

    private void putLocal(String key, Object value, Duration ttl) {
        long expireAt = System.nanoTime() + (ttl != null ? ttl.toNanos() : DEFAULT_TTL.toNanos());
        localCache.put(key, new CacheValue(value, expireAt));
    }

    /**
     * 仅本地失效（用于接收广播时，不影响L2）
     */
    public void invalidateLocal(String key) {
        localCache.invalidate(key);
    }

    public CacheStats localStats() {
        return localCache.stats();
    }

    public long localSize() {
        return localCache.estimatedSize();
    }
}