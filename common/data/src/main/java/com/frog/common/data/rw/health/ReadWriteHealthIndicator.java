package com.frog.common.data.rw.health;

import com.frog.common.data.rw.config.ReadWriteAutoConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离健康指示器
 * <p>
 * 用于 Spring Boot Actuator 健康端点
 * <p>
 * 注意：需要 spring-boot-starter-actuator 依赖
 *
 * @author Deng
 * @since 2025-12-16
 */
@RequiredArgsConstructor
public class ReadWriteHealthIndicator implements HealthIndicator {
    private final SlaveHealthChecker healthChecker;
    private final ReadWriteAutoConfiguration.ReadWriteDataSourceProvider dataSourceProvider;

    @Override
    public Health health() {
        Map<String, SlaveHealthChecker.HealthStatus> allStatus = healthChecker.getAllHealthStatus();

        if (allStatus.isEmpty()) {
            return Health.unknown()
                    .withDetail("message", "No slave datasources configured")
                    .build();
        }

        Map<String, Object> details = new HashMap<>();
        boolean anyUnavailable = false;
        long maxLag = 0;

        for (Map.Entry<String, SlaveHealthChecker.HealthStatus> entry : allStatus.entrySet()) {
            SlaveHealthChecker.HealthStatus status = entry.getValue();

            Map<String, Object> slaveDetails = new HashMap<>();
            slaveDetails.put("available", status.available());
            slaveDetails.put("replicationLagMs", status.replicationLagMs());
            slaveDetails.put("consecutiveFailures", status.consecutiveFailures());

            details.put(entry.getKey(), slaveDetails);

            if (!status.available()) {
                anyUnavailable = true;
            }
            maxLag = Math.max(maxLag, status.replicationLagMs());
        }

        details.put("groups", dataSourceProvider.getGroupNames());
        details.put("maxReplicationLagMs", maxLag);

        if (anyUnavailable) {
            return Health.down()
                    .withDetails(details)
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .build();
    }
}
