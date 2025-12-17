package com.frog.common.security.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security 的一些基本配置
 *
 * @author Deng
 * createData 2025/10/14 14:45
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "security")
@Data
public class SecurityProperties {

    private Integer maxLoginAttempts = 5;
    private Long lockDuration = 1800000L; // 30分钟
    private Integer passwordExpireDays = 90;
    private Long sessionTimeout = 1800000L; // 30分钟
}