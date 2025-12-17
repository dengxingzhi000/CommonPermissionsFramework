package com.frog.system.sync.handler;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.frog.common.integration.sync.event.DataSyncEvent;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 角色数据同步处理器
 * <p>
 * 处理角色数据变更，同步更新冗余字段到其他库：
 * - db_approval.sys_permission_approval (role_names)
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleSyncHandler implements DataSyncHandler {

    @Override
    public String getAggregateType() {
        return "Role";
    }

    @Override
    public void handle(DataSyncEvent event) throws DataSyncException {
        UUID roleId = UUID.fromString(event.getPrimaryId());
        Map<String, Object> data = event.getAfterData();

        log.debug("[RoleSync] Handling event: roleId={}, type={}", roleId, event.getEventType());

        try {
            switch (event.getEventType()) {
                case INSERT, UPDATE -> {
                    String roleName = (String) data.get("roleName");
                    String roleCode = (String) data.get("roleCode");
                    syncToApprovalDb(roleId, roleName, roleCode);
                }
                case DELETE -> {
                    // 角色删除时，更新相关审批记录
                    markRoleDeletedInApprovalDb(roleId);
                }
                default -> log.warn("[RoleSync] Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            throw new DataSyncException("Failed to sync role: " + roleId, e, true);
        }
    }

    /**
     * 同步角色信息到 approval 库
     */
    @DS("approval")
    @Transactional
    public void syncToApprovalDb(UUID roleId, String roleName, String roleCode) {
        // 更新包含该角色的审批记录的 role_names 数组
        // 这里需要处理 PostgreSQL 的数组类型
        log.debug("[RoleSync] Would update approval records for role: {}, name: {}",
                roleId, roleName);
    }

    @DS("approval")
    @Transactional
    public void markRoleDeletedInApprovalDb(UUID roleId) {
        log.debug("[RoleSync] Would mark role as deleted in approval db: {}", roleId);
    }
}
