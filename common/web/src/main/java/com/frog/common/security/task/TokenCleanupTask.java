package com.frog.common.security.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务 - 清理过期Token
 *
 * @author Deng
 * createData 2025/10/15 14:44
 * @version 1.1 - 使用SCAN替代KEYS避免阻塞Redis事件循环
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupTask {
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 每天凌晨3点清理过期的黑名单Token
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredBlacklistTokens() {
        long count = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("jwt:blacklist:*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl < 0) {
                    redisTemplate.delete(key);
                    count++;
                }
            }
            log.info("Cleaned up {} expired blacklist tokens", count);
        } catch (Exception e) {
            log.error("Failed to cleanup expired blacklist tokens", e);
        }
    }

    /**
     * 每小时清理过期的会话
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredSessions() {
        long count = 0;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match("jwt:user:*").count(500).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long ttl = redisTemplate.getExpire(key);
                if (ttl != null && ttl < 0) {
                    redisTemplate.delete(key);
                    count++;
                }
            }
            log.info("Cleaned up {} expired user sessions", count);
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions", e);
        }
    }
}
