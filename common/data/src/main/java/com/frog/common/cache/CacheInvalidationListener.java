package com.frog.common.cache;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订阅缓存失效消息，执行本地L1失效
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener implements MessageListener {
    private final MultiLevelCache multiLevelCache;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            // 兼容 Jackson对字符串的引号包装
            if (body.length() >= 2 && body.startsWith("\"") && body.endsWith("\"")) {
                body = body.substring(1, body.length() - 1);
            }
            if (!body.isEmpty()) {
                multiLevelCache.invalidateLocal(body);
                log.debug("Local cache invalidated by pub/sub, key={}", body);
            }
        } catch (Exception e) {
            log.warn("Failed to process cache invalidation message", e);
        }
    }
}

