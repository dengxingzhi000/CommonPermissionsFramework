package com.frog.common.web.health;

import com.frog.common.cache.spring.TwoLevelCache;
import com.frog.common.cache.spring.TwoLevelCacheManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CacheHealthIndicator implements HealthIndicator {
    private final RedisTemplate<String, Object> redisTemplate;
    private final TwoLevelCacheManager cacheManager;

    @Override
    public Health health() {
        try {
            String pong = redisTemplate.getRequiredConnectionFactory().getConnection().ping();
            boolean redisOk = pong != null && !pong.isEmpty();

            // Aggregate L1 cache sizes from all TwoLevelCache instances
            Map<String, Long> cacheSizes = new HashMap<>();
            long totalL1Size = 0;
            for (Map.Entry<String, TwoLevelCache> entry : cacheManager.currentCaches().entrySet()) {
                long size = entry.getValue().localSize();
                cacheSizes.put(entry.getKey(), size);
                totalL1Size += size;
            }

            Health.Builder b = redisOk ? Health.up() : Health.down();
            return b.withDetail("redis", redisOk ? "UP" : "DOWN")
                    .withDetail("twolevel.l1.total", totalL1Size)
                    .withDetail("twolevel.l1.bycache", cacheSizes)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}

