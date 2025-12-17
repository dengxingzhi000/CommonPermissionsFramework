package com.frog.common.data.rw.endpoint;

import com.frog.common.data.rw.config.ReadWriteAutoConfiguration;
import com.frog.common.data.rw.health.SlaveHealthChecker;
import com.frog.common.data.rw.routing.ReadWriteRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 读写分离管理端点
 * <p>
 * 提供运行时管理能力：
 * - 查看从库状态
 * - 手动摘除/恢复从库
 * - 查看健康信息
 * <p>
 * 访问路径：/actuator/readwrite
 * <p>
 * 注意：需要 spring-boot-starter-actuator 依赖
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Endpoint(id = "readwrite")
@RequiredArgsConstructor
public class ReadWriteEndpoint {
    private final ReadWriteAutoConfiguration.ReadWriteDataSourceProvider dataSourceProvider;
    private final SlaveHealthChecker healthChecker;

    /**
     * 获取所有读写分离状态
     * <p>
     * GET /actuator/readwrite
     */
    @ReadOperation
    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();

        Set<String> groups = dataSourceProvider.getGroupNames();
        result.put("groups", groups);

        Map<String, Object> groupDetails = new HashMap<>();
        for (String groupName : groups) {
            groupDetails.put(groupName, buildGroupInfo(groupName));
        }

        result.put("details", groupDetails);
        return result;
    }

    /**
     * 获取指定组的状态
     * <p>
     * GET /actuator/readwrite/{groupName}
     */
    @ReadOperation
    public Map<String, Object> statusByGroup(@Selector String groupName) {
        Map<String, Object> result = new HashMap<>();

        try {
            result.put("group", groupName);
            result.putAll(buildGroupInfo(groupName));
        } catch (IllegalArgumentException e) {
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 手动摘除从库
     * <p>
     * POST /actuator/readwrite with {"groupName": "xxx", "slaveName": "xxx", "action": "markUnavailable"}
     */
    @WriteOperation
    public Map<String, Object> operate(String groupName, String slaveName, String action) {
        if ("markAvailable".equalsIgnoreCase(action)) {
            return doMarkAvailable(groupName, slaveName);
        } else {
            return doMarkUnavailable(groupName, slaveName);
        }
    }

    /**
     * 构建组信息
     */
    private Map<String, Object> buildGroupInfo(String groupName) {
        Map<String, Object> groupInfo = new HashMap<>();

        ReadWriteRoutingDataSource ds = dataSourceProvider.getDataSource(groupName);
        groupInfo.put("slaveAvailability", ds.getSlaveAvailability());
        groupInfo.put("healthStatus", buildHealthDetails(groupName));

        return groupInfo;
    }

    /**
     * 构建健康详情
     */
    private Map<String, Object> buildHealthDetails(String groupName) {
        Map<String, Object> healthDetails = new HashMap<>();
        Map<String, SlaveHealthChecker.HealthStatus> allStatus = healthChecker.getAllHealthStatus();

        String prefix = groupName + ".";
        for (Map.Entry<String, SlaveHealthChecker.HealthStatus> entry : allStatus.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String slaveName = entry.getKey().substring(prefix.length());
                SlaveHealthChecker.HealthStatus status = entry.getValue();
                healthDetails.put(slaveName, Map.of(
                        "available", status.available(),
                        "replicationLagMs", status.replicationLagMs(),
                        "consecutiveFailures", status.consecutiveFailures()
                ));
            }
        }

        return healthDetails;
    }

    private Map<String, Object> doMarkUnavailable(String groupName, String slaveName) {
        Map<String, Object> result = new HashMap<>();

        try {
            ReadWriteRoutingDataSource ds = dataSourceProvider.getDataSource(groupName);
            ds.markSlaveUnavailable(slaveName);

            log.warn("[RW-Endpoint] Manually marked slave [{}] in group [{}] as UNAVAILABLE",
                    slaveName, groupName);

            result.put("success", true);
            result.put("message", String.format("Slave [%s.%s] marked as unavailable", groupName, slaveName));
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    private Map<String, Object> doMarkAvailable(String groupName, String slaveName) {
        Map<String, Object> result = new HashMap<>();

        try {
            ReadWriteRoutingDataSource ds = dataSourceProvider.getDataSource(groupName);
            ds.markSlaveAvailable(slaveName);

            log.info("[RW-Endpoint] Manually marked slave [{}] in group [{}] as AVAILABLE",
                    slaveName, groupName);

            result.put("success", true);
            result.put("message", String.format("Slave [%s.%s] marked as available", groupName, slaveName));
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}
