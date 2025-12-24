package com.frog.common.web.metrics;

import com.frog.common.cache.spring.TwoLevelCache;
import com.frog.common.cache.spring.TwoLevelCacheManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.FunctionCounter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CacheMetricsConfig {

    @Bean
    public MeterBinder cacheMeters(TwoLevelCacheManager manager) {
        return registry -> {
            // TwoLevelCache per-cache metrics
            for (Map.Entry<String, TwoLevelCache> e : manager.currentCaches().entrySet()) {
                String cacheName = e.getKey();
                TwoLevelCache cache = e.getValue();

                Gauge.builder("cache.local.size", cache, TwoLevelCache::localSize)
                        .description("TwoLevel local cache size")
                        .tag("cache", cacheName)
                        .register(registry);

                FunctionCounter.builder("cache.local.hits", cache, c ->
                                (double) c.getLocalStats().hitCount())
                        .description("TwoLevel local cache hits")
                        .tag("cache", cacheName)
                        .register(registry);

                FunctionCounter.builder("cache.local.misses", cache, c ->
                                (double) c.getLocalStats().missCount())
                        .description("TwoLevel local cache misses")
                        .tag("cache", cacheName)
                        .register(registry);
            }
        };
    }
}

