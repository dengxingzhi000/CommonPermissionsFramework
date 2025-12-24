package com.frog.common.redis.config;

import com.frog.common.cache.spring.TwoLevelCacheInvalidationListener;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Primary;
import com.frog.common.cache.spring.TwoLevelCacheManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 配置类
 *
 * @author Deng
 * createData 2025/10/15 14:33
 * @version 1.0
 */
@Configuration
@EnableCaching
public class RedisConfig {
    private static final String TWOLEVEL_INVALIDATION_CHANNEL = "cache:invalidation:twolevel";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        JacksonJsonRedisSerializer<Object> serializer = new JacksonJsonRedisSerializer<>(Object.class);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key 采用String的序列化方式
        template.setKeySerializer(stringSerializer);
        // hash 的key也采用String的序列化方式
        template.setHashKeySerializer(stringSerializer);
        // value 序列化方式采用jackson
        template.setValueSerializer(serializer);
        // hash  的value序列化方式采用jackson
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisMessageListenerContainer twoLevelCacheListenerContainer(
            RedisConnectionFactory connectionFactory,
            TwoLevelCacheInvalidationListener twoLevelListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new MessageListenerAdapter(twoLevelListener), new PatternTopic(TWOLEVEL_INVALIDATION_CHANNEL));
        return container;
    }

    @Bean
    @Primary
    public CacheManager twoLevelCacheManager(RedisTemplate<String, Object> redisTemplate) {
        Duration defaultTtl = Duration.ofHours(1);
        Map<String, Duration> ttls = new HashMap<>();
        ttls.put("user", Duration.ofMinutes(30));
        ttls.put("userInfo", Duration.ofMinutes(30));
        ttls.put("userRoles", Duration.ofHours(1));
        ttls.put("userPermissions", Duration.ofHours(1));
        ttls.put("permissionTree", Duration.ofHours(2));
        ttls.put("permissionMapping", Duration.ofMinutes(5));
        ttls.put("roles", Duration.ofHours(1));
        ttls.put("role", Duration.ofHours(1));
        long localMaxSize = 10_000L;
        return new TwoLevelCacheManager(redisTemplate, defaultTtl, ttls, localMaxSize);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1)); // 默认缓存1小时

        // 为不同的缓存设置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("user", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(30))); // 用户缓存30分钟

        cacheConfigurations.put("userInfo", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofMinutes(30))); // 用户信息缓存30分钟

        cacheConfigurations.put("userRoles", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1))); // 用户角色缓存1小时

        cacheConfigurations.put("userPermissions", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1))); // 用户权限缓存1小时

        cacheConfigurations.put("permissionTree", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(2))); // 权限树缓存2小时

        cacheConfigurations.put("roles", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1))); // 角色列表缓存1小时

        cacheConfigurations.put("role", RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer()))
                .entryTtl(Duration.ofHours(1))); // 角色缓存1小时

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    private JacksonJsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        return new JacksonJsonRedisSerializer<>(Object.class);
    }
}
