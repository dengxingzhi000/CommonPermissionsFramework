package com.frog.common.security.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 会话管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManager {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSIONS_PREFIX = "user:sessions:";
    private static final String ONLINE_USERS_KEY = "online:users";

    /**
     * 创建会话
     */
    public String createSession(UUID userId, String username,
                                String deviceId, String ipAddress,
                                Duration sessionTimeout) {
        String sessionId = UUID.randomUUID().toString();

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", userId.toString());
        sessionData.put("username", username);
        sessionData.put("deviceId", deviceId);
        sessionData.put("ipAddress", ipAddress);
        sessionData.put("loginTime", LocalDateTime.now().toString());
        sessionData.put("lastActivityTime", LocalDateTime.now().toString());

        String sessionKey = SESSION_PREFIX + sessionId;
        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        redisTemplate.expire(sessionKey, sessionTimeout);

        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, sessionTimeout);

        redisTemplate.opsForZSet().add(ONLINE_USERS_KEY,
                userId.toString(), System.currentTimeMillis());

        log.info("Session created: sessionId={}, userId={}, deviceId={}",
                sessionId, userId, deviceId);

        return sessionId;
    }

    /**
     * 更新会话活动时间
     */
    public void updateActivity(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;

        if (redisTemplate.hasKey(sessionKey)) {
            redisTemplate.opsForHash().put(sessionKey, "lastActivityTime",
                    LocalDateTime.now().toString());

            // 续长当前 TTL，如无 TTL 则回退 30 分钟
            Long ttlSeconds = redisTemplate.getExpire(sessionKey);
            if (ttlSeconds != null && ttlSeconds > 0) {
                redisTemplate.expire(sessionKey, Duration.ofSeconds(ttlSeconds));
            } else {
                redisTemplate.expire(sessionKey, Duration.ofMinutes(30));
            }
        }
    }

    /**
     * 关闭会话
     */
    public void destroySession(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;

        Object userIdObj = redisTemplate.opsForHash().get(sessionKey, "userId");
        if (userIdObj != null) {
            String userId = userIdObj.toString();

            String userSessionsKey = USER_SESSIONS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

            Long sessionCount = redisTemplate.opsForSet().size(userSessionsKey);
            if (sessionCount != null && sessionCount == 0) {
                redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, userId);
            }
        }

        redisTemplate.delete(sessionKey);

        log.info("Session destroyed: sessionId={}", sessionId);
    }

    /**
     * 关闭用户的所有会话
     */
    public void destroyAllUserSessions(UUID userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;

        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (Object sessionId : sessionIds) {
                destroySession(sessionId.toString());
            }
        }

        redisTemplate.opsForZSet().remove(ONLINE_USERS_KEY, userId.toString());

        log.info("All sessions destroyed for user: userId={}", userId);
    }

    /**
     * 获取用户的所有会话
     */
    public List<Map<String, Object>> getUserSessions(UUID userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId;
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);

        List<Map<String, Object>> sessions = new ArrayList<>();
        if (sessionIds != null) {
            for (Object sessionId : sessionIds) {
                String sessionKey = SESSION_PREFIX + sessionId;
                Map<Object, Object> sessionData = redisTemplate.opsForHash()
                        .entries(sessionKey);

                if (!sessionData.isEmpty()) {
                    Map<String, Object> session = new HashMap<>();
                    session.put("sessionId", sessionId);
                    session.putAll(convertMap(sessionData));
                    sessions.add(session);
                }
            }
        }

        return sessions;
    }

    /**
     * 获取在线用户列表
     */
    public List<String> getOnlineUsers() {
        Set<Object> userIds = redisTemplate.opsForZSet()
                .range(ONLINE_USERS_KEY, 0, -1);

        return userIds != null ?
                userIds.stream().map(Object::toString).toList() :
                Collections.emptyList();
    }

    /**
     * 获取在线用户数量
     */
    public Long getOnlineUserCount() {
        return redisTemplate.opsForZSet().size(ONLINE_USERS_KEY);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(UUID userId) {
        Double score = redisTemplate.opsForZSet()
                .score(ONLINE_USERS_KEY, userId.toString());
        return score != null;
    }

    /**
     * 清理过期会话（定时任务调用）
     */
    public void cleanupExpiredSessions() {
        log.info("Starting expired sessions cleanup");

        try {
            java.util.concurrent.atomic.AtomicInteger cleaned = new java.util.concurrent.atomic.AtomicInteger();
            redisTemplate.execute(connection -> {
                try (var cursor = connection.keyCommands().scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(SESSION_PREFIX + "*")
                                .count(500)
                                .build())) {
                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        Long ttl = connection.keyCommands().ttl(key);
                        if (ttl != null && ttl < 0) {
                            String keyStr = new String(key);
                            String sessionId = keyStr.substring(SESSION_PREFIX.length());
                            destroySession(sessionId);
                            cleaned.incrementAndGet();
                        }
                    }
                }
                return null;
            }, false, true);
            if (cleaned.get() > 0) {
                log.info("Cleaned {} expired sessions", cleaned.get());
            }
        } catch (Exception e) {
            log.error("Error cleaning expired sessions", e);
        }
    }

    /**
     * 获取用户的会话统计信息
     */
    public Map<String, Object> getUserSessionStats(UUID userId) {
        Map<String, Object> stats = new HashMap<>();

        List<Map<String, Object>> sessions = getUserSessions(userId);
        stats.put("sessionCount", sessions.size());
        stats.put("isOnline", isUserOnline(userId));

        if (!sessions.isEmpty()) {
            Optional<String> latestLogin = sessions.stream()
                    .map(s -> (String) s.get("loginTime"))
                    .max(String::compareTo);
            stats.put("latestLoginTime", latestLogin.orElse(null));

            long deviceCount = sessions.stream()
                    .map(s -> s.get("deviceId"))
                    .distinct()
                    .count();
            stats.put("deviceCount", deviceCount);
        }

        return stats;
    }

    /**
     * 限制用户的并发会话数
     */
    public boolean checkConcurrentSessions(UUID userId, int maxSessions) {
        List<Map<String, Object>> sessions = getUserSessions(userId);

        if (sessions.size() >= maxSessions) {
            sessions.stream()
                    .min(Comparator.comparing(s ->
                            (String) s.get("loginTime")))
                    .ifPresent(oldestSession -> {
                        String sessionId = (String) oldestSession.get("sessionId");
                        destroySession(sessionId);
                        log.info("Oldest session destroyed due to limit: sessionId={}",
                                sessionId);
                    });

            return false;
        }

        return true;
    }

    private Map<String, Object> convertMap(Map<Object, Object> map) {
        Map<String, Object> result = new HashMap<>();
        map.forEach((k, v) -> result.put(k.toString(), v));
        return result;
    }
}
