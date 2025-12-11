package com.frog.common.cache.spring;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class TwoLevelCacheManager implements CacheManager {
    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration defaultTtl;
    private final Map<String, Duration> ttlByCache;
    private final long localMaxSize;

    private final Map<String, TwoLevelCache> caches = new ConcurrentHashMap<>();

    @Override
    public Cache getCache(@NonNull String name) {
        return caches.computeIfAbsent(name, n ->
                new TwoLevelCache(n, ttlByCache.getOrDefault(n, defaultTtl), redisTemplate, localMaxSize));
    }

    @Override
    @NonNull
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(ttlByCache.keySet());
    }

    public void invalidateLocal(String cacheName, String keyOrWildcard) {
        TwoLevelCache cache = caches.get(cacheName);
        if (cache == null) return;
        if ("*".equals(keyOrWildcard)) {
            cache.clearLocal();
        } else {
            cache.invalidateLocal(keyOrWildcard);
        }
    }

    public Map<String, TwoLevelCache> currentCaches() {
        return Collections.unmodifiableMap(caches);
    }
}
