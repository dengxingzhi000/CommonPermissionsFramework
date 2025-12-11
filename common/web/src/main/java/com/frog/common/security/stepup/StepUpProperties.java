package com.frog.common.security.stepup;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.stepup")
@Data
public class StepUpProperties {

    private boolean enabled = true;
    // 工作时间窗口（含）
    private int businessStartHour = 9;   // 09:00
    private int businessEndHour = 18;    // 18:00
    // 是否启用新设备触发
    private boolean newDeviceTrigger = true;
    // 策略文件路径（可选）：优先该路径，其次 classpath:security/stepup-policy.yaml，最后 docs/security/stepup-policy.yaml
    private String policyPath;
    // 策略缓存刷新秒数（TTL）
    private int refreshSeconds = 60;
}
