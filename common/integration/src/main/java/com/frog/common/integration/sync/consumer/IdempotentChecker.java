package com.frog.common.integration.sync.consumer;

import com.frog.common.integration.sync.config.DataSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 幂等性检查器
 * <p>
 * 基于 Redis 实现消息去重，防止重复消费
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotentChecker {
    private final StringRedisTemplate redisTemplate;
    private final DataSyncProperties.IdempotentConfig config;

    /**
     * 尝试获取处理权
     * <p>
     * 使用 Redis SETNX 实现分布式锁语义
     *
     * @param eventId 事件 ID
     * @return true 如果获取成功（首次处理），false 如果已处理过
     */
    public boolean tryAcquire(String eventId) {
        if (!config.isEnabled()) {
            return true;
        }

        String key = config.getKeyPrefix() + eventId;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "processing", Duration.ofSeconds(config.getExpireSeconds()));

        if (Boolean.TRUE.equals(success)) {
            log.debug("[Idempotent] Acquired processing lock: eventId={}", eventId);
            return true;
        } else {
            log.debug("[Idempotent] Duplicate event detected: eventId={}", eventId);
            return false;
        }
    }

    /**
     * 标记处理完成
     *
     * @param eventId 事件 ID
     */
    public void markCompleted(String eventId) {
        if (!config.isEnabled()) {
            return;
        }

        String key = config.getKeyPrefix() + eventId;
        redisTemplate.opsForValue().set(key, "completed",
                Duration.ofSeconds(config.getExpireSeconds()));
        log.debug("[Idempotent] Marked as completed: eventId={}", eventId);
    }

    /**
     * 标记处理失败（释放锁，允许重试）
     *
     * @param eventId 事件 ID
     */
    public void markFailed(String eventId) {
        if (!config.isEnabled()) {
            return;
        }

        String key = config.getKeyPrefix() + eventId;
        redisTemplate.delete(key);
        log.debug("[Idempotent] Marked as failed, lock released: eventId={}", eventId);
    }

    /**
     * 检查是否已处理
     *
     * @param eventId 事件 ID
     * @return true 如果已处理
     */
    public boolean isProcessed(String eventId) {
        if (!config.isEnabled()) {
            return false;
        }

        String key = config.getKeyPrefix() + eventId;
        String status = redisTemplate.opsForValue().get(key);
        return "completed".equals(status);
    }
}
