package com.frog.common.cache.spring;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;

@Slf4j
public class TwoLevelCache implements org.springframework.cache.Cache {
    private static final String CHANNEL = "cache:invalidation:twolevel";

    private final String name;
    private final Duration ttl;
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.github.benmanes.caffeine.cache.Cache<@NonNull String, Object> local;

    public TwoLevelCache(String name, Duration ttl, RedisTemplate<String, Object> redisTemplate, long maxSize) {
        this.name = name;
        this.ttl = ttl;
        this.redisTemplate = redisTemplate;
        this.local = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    public ValueWrapper get(@NonNull Object key) {
        Object v = lookup(key);
        return (v != null) ? () -> v : null;
    }

    @Override
    public <T> T get(@NonNull Object key, Class<T> type) {
        Object v = lookup(key);
        if (v == null) {
            return null;
        }
        if (type == null) {
            return uncheckedCast(v);
        }
        return type.cast(v);
    }

    @Override
    public <T> T get(@NonNull Object key, @NonNull Callable<T> valueLoader) {
        Object v = lookup(key);
        if (v != null) {
            return uncheckedCast(v);
        }
        try {
            T loaded = Objects.requireNonNull(valueLoader.call());
            put(key, loaded);
            return loaded;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(@NonNull Object key, Object value) {
        String k = keyString(key);
        local.put(k, value);
        try {
            redisTemplate.opsForValue().set(redisKey(k), value, ttl);
            redisTemplate.convertAndSend(CHANNEL, name + "|" + k);
        } catch (Exception e) {
            log.warn("TwoLevelCache put redis failed: {}", e.getMessage());
        }
    }

    @Override
    public ValueWrapper putIfAbsent(@NonNull Object key, Object value) {
        Object existing = lookup(key);
        if (existing == null) {
            put(key, value);
            return null;
        }
        final Object finalExisting = existing;
        return () -> finalExisting;
    }

    @Override
    public void evict(@NonNull Object key) {
        String k = keyString(key);
        local.invalidate(k);
        try {
            redisTemplate.delete(redisKey(k));
            redisTemplate.convertAndSend(CHANNEL, name + "|" + k);
        } catch (Exception e) {
            log.warn("TwoLevelCache evict redis failed: {}", e.getMessage());
        }
    }

    @Override
    public void clear() {
        local.invalidateAll();
        try {
            redisTemplate.execute(connection -> {
                try (var cursor = connection.keyCommands().scan(
                        ScanOptions.scanOptions()
                                .match(redisKey("*"))
                                .count(500)
                                .build())) {
                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        connection.keyCommands().del(key);
                    }
                }
                return null;
            }, false, true);
            redisTemplate.convertAndSend(CHANNEL, name + "|*");
        } catch (Exception e) {
            log.warn("TwoLevelCache clear redis failed: {}", e.getMessage());
        }
    }

    public void invalidateLocal(Object key) {
        local.invalidate(keyString(key));
    }

    public void clearLocal() {
        local.invalidateAll();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats getLocalStats() {
        return local.stats();
    }

    public long localSize() {
        return local.estimatedSize();
    }

    private Object lookup(Object key) {
        String k = keyString(key);
        Object v = local.getIfPresent(k);
        if (v != null) return v;
        try {
            v = redisTemplate.opsForValue().get(redisKey(k));
        } catch (Exception e) {
            log.warn("TwoLevelCache get redis failed: {}", e.getMessage());
        }
        if (v != null) {
            local.put(k, v);
        }
        return v;
    }

    @NonNull
    private String keyString(Object key) {
        return String.valueOf(key);
    }

    private String redisKey(String k) {
        return name + ":" + k;
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object value) {
        return (T) value;
    }
}
