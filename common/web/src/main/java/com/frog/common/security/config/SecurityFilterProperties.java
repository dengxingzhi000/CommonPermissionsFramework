package com.frog.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全过滤相关配置
 */
@Component
@ConfigurationProperties(prefix = "frog.security.sql-filter")
@Data
public class SecurityFilterProperties {

    /** 是否启用 SQL/XSS 过滤 */
    private boolean enabled = true;

    /** SQL/XSS 过滤动作：ALERT 仅告警，BLOCK 拦截 */
    private SqlFilterMode mode = SqlFilterMode.ALERT;

    /** 是否启用 XSS 简单检测（建议主要依赖 CSP/输出编码） */
    private boolean xssEnabled = true;

    /** 按路径排除（Ant 表达式），例如：/api/public/** */
    private List<String> excludePaths = new ArrayList<>();

    public enum SqlFilterMode {
        ALERT,
        BLOCK
    }
}
