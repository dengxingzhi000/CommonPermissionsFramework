package com.frog.common.data.rw.dynamic;

import com.frog.common.data.rw.config.ReadWriteProperties;
import com.frog.common.data.rw.routing.ReadWriteRoutingDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * 动态数据源刷新器
 * <p>
 * 监听 Nacos 配置变更，动态调整：
 * - 从库权重
 * - 从库启用/禁用
 * - 负载均衡策略
 * - 健康检查参数
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicDataSourceRefresher {
    private static final String REFRESH_EVENT_CLASS = "org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent";
    private static final String ENV_CHANGE_EVENT_CLASS = "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    private final ReadWriteProperties properties;
    private final Map<String, ReadWriteRoutingDataSource> routingDataSources;
    private final Environment environment;

    /**
     * 监听配置刷新事件
     * <p>
     * 兼容多种 Spring Cloud 事件：
     * - RefreshScopeRefreshedEvent
     * - EnvironmentChangeEvent
     */
    @EventListener
    public void onApplicationEvent(ApplicationEvent event) {
        String eventClassName = event.getClass().getName();

        // 检查是否是配置刷新相关事件
        if (eventClassName.equals(REFRESH_EVENT_CLASS) ||
                eventClassName.equals(ENV_CHANGE_EVENT_CLASS)) {

            log.info("[Dynamic-DS] Configuration refresh detected ({}), reloading datasource settings...",
                    event.getClass().getSimpleName());

            try {
                // 重新绑定配置
                rebindProperties();
                // 刷新从库设置
                refreshSlaveSettings();
                log.info("[Dynamic-DS] Datasource settings refreshed successfully");
            } catch (Exception e) {
                log.error("[Dynamic-DS] Failed to refresh datasource settings", e);
            }
        }
    }

    /**
     * 重新绑定配置属性
     */
    private void rebindProperties() {
        try {
            ReadWriteProperties newProperties = Binder.get(environment)
                    .bind("spring.datasource.rw", ReadWriteProperties.class)
                    .orElse(null);

            if (newProperties != null) {
                // 更新可变配置
                properties.setReadMasterAfterWrite(newProperties.getReadMasterAfterWrite());
                properties.setReplicationLagTolerance(newProperties.getReplicationLagTolerance());
                properties.setHealthCheckEnabled(newProperties.isHealthCheckEnabled());
                properties.setFailureThreshold(newProperties.getFailureThreshold());

                // 更新各组配置
                for (var entry : newProperties.getGroups().entrySet()) {
                    var existingGroup = properties.getGroups().get(entry.getKey());
                    if (existingGroup != null) {
                        existingGroup.setSlavesEnabled(entry.getValue().isSlavesEnabled());
                        existingGroup.setLoadBalance(entry.getValue().getLoadBalance());

                        // 更新从库配置
                        for (var newSlave : entry.getValue().getSlaves()) {
                            for (var existingSlave : existingGroup.getSlaves()) {
                                if (existingSlave.getName().equals(newSlave.getName())) {
                                    existingSlave.setWeight(newSlave.getWeight());
                                    existingSlave.setAvailable(newSlave.isAvailable());
                                }
                            }
                        }
                    }
                }

                log.debug("[Dynamic-DS] Properties rebound successfully");
            }
        } catch (Exception e) {
            log.warn("[Dynamic-DS] Failed to rebind properties: {}", e.getMessage());
        }
    }

    /**
     * 刷新从库设置
     * <p>
     * 支持热更新的配置：
     * - 从库权重
     * - 从库启用/禁用
     * - 读写一致性时间窗口
     * - 复制延迟容忍
     */
    private void refreshSlaveSettings() {
        for (Map.Entry<String, ReadWriteProperties.DataSourceGroup> groupEntry :
                properties.getGroups().entrySet()) {

            String groupName = groupEntry.getKey();
            ReadWriteProperties.DataSourceGroup group = groupEntry.getValue();
            ReadWriteRoutingDataSource routingDs = routingDataSources.get(groupName);

            if (routingDs == null) {
                log.warn("[Dynamic-DS] Group [{}] not found in routing datasources", groupName);
                continue;
            }

            // 更新从库可用性
            if (!group.isSlavesEnabled()) {
                // 禁用所有从库
                log.info("[Dynamic-DS] Group [{}] slaves disabled, marking all as unavailable", groupName);
                for (var slave : group.getSlaves()) {
                    routingDs.markSlaveUnavailable(slave.getName());
                }
            } else {
                // 根据配置更新各从库状态
                for (var slave : group.getSlaves()) {
                    if (slave.isAvailable()) {
                        routingDs.markSlaveAvailable(slave.getName());
                    } else {
                        routingDs.markSlaveUnavailable(slave.getName());
                    }
                    log.debug("[Dynamic-DS] Group [{}] slave [{}] weight={}, available={}",
                            groupName, slave.getName(), slave.getWeight(), slave.isAvailable());
                }
            }
        }

        log.info("[Dynamic-DS] Global settings: readMasterAfterWrite={}, replicationLagTolerance={}",
                properties.getReadMasterAfterWrite(), properties.getReplicationLagTolerance());
    }

    /**
     * 手动触发刷新
     */
    public void forceRefresh() {
        log.info("[Dynamic-DS] Force refresh triggered");
        rebindProperties();
        refreshSlaveSettings();
    }

    /**
     * 获取当前配置快照
     */
    public Map<String, Object> getConfigSnapshot() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "loadBalance", properties.getLoadBalance(),
                "readMasterAfterWrite", properties.getReadMasterAfterWrite().toString(),
                "replicationLagTolerance", properties.getReplicationLagTolerance().toString(),
                "healthCheckEnabled", properties.isHealthCheckEnabled(),
                "failureThreshold", properties.getFailureThreshold(),
                "groups", properties.getGroups().keySet()
        );
    }
}
