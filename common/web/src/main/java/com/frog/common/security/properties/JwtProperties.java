package com.frog.common.security.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Jwt 配置类
 *
 * @author Deng
 * createData 2025/10/11 11:05
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    /**
     * JWT签名密钥（至少512位）
     */
    private String secret;

    /**
     * Access Token过期时间（毫秒）
     * 默认: 2小时
     */
    private Long expiration = 7200000L;

    /**
     * Refresh Token过期时间（毫秒）
     * 默认: 7天
     */
    private Long refreshExpiration = 604800000L;

    /**
     * Token 签发者
     */
    private String issuer = "nearsync-auth-service";

    /**
     * Token 请求头名称
     */
    private String header = "Authorization";

    /**
     * Token 前缀
     */
    private String prefix = "Bearer ";

    /**
     * 是否启用严格的IP检查
     * true: Token只能在签发的IP上使用
     * false: 允许IP变更
     */
    private boolean strictIpCheck = false;

    /**
     * 是否启用设备指纹验证
     */
    private boolean deviceFingerprintEnabled = true;

    /**
     * Token自动续期阈值（毫秒）
     * 当Token剩余时间小于此值时，自动续期
     * 默认: 30分钟
     */
    private Long autoRenewThreshold = 1800000L;

    /**
     * 是否启用 Token自动续期
     */
    private boolean autoRenewEnabled = false;

    /**
     * 同一用户最大并发会话数
     * 0表示不限制
     */
    private Integer maxConcurrentSessions = 0;

    /**
     * 会话互斥策略
     * ALLOW_ALL: 允许所有会话
     * REPLACE_OLD: 新会话替换旧会话
     * REJECT_NEW: 拒绝新会话
     */
    private SessionPolicy sessionPolicy = SessionPolicy.ALLOW_ALL;

    /**
     * Token黑名单清理策略
     * LAZY: 懒清理（过期时自动删除）
     * SCHEDULED: 定时清理
     */
    private CleanupStrategy blacklistCleanupStrategy = CleanupStrategy.LAZY;

    /**
     * 黑名单定时清理间隔（小时）
     */
    private Integer blacklistCleanupInterval = 24;

    public enum SessionPolicy {
        ALLOW_ALL,
        REPLACE_OLD,
        REJECT_NEW
    }

    public enum CleanupStrategy {
        LAZY,
        SCHEDULED
    }
}