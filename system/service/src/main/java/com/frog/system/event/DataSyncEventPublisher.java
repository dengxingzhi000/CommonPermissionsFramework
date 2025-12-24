package com.frog.system.event;

import com.frog.common.integration.sync.event.DataSyncEvent;
import com.frog.common.integration.sync.event.DataSyncEventType;
import com.frog.common.integration.sync.publisher.DataSyncPublisher;
import com.frog.system.domain.entity.SysDept;
import com.frog.system.domain.entity.SysRole;
import com.frog.system.domain.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据同步事件发布器
 * <p>
 * 封装业务实体到 DataSyncEvent 的转换，
 * 委托给全局 DataSyncPublisher 发布到 Kafka
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncEventPublisher {
    private final DataSyncPublisher publisher;

    public void publishUserCreated(SysUser user) {
        publishEvent(DataSyncEvent.ofInsert("User", user.getId().toString(), buildUserData(user)),
                "db_user", "sys_user", "UserCreated", user.getId());
    }

    public void publishUserUpdated(SysUser user) {
        publishEvent(DataSyncEvent.create("User", user.getId().toString(), DataSyncEventType.UPDATE,
                        buildUserData(user)),
                "db_user", "sys_user", "UserUpdated", user.getId());
    }

    public void publishUserDeleted(UUID userId) {
        publishEvent(DataSyncEvent.ofDelete("User", userId.toString()),
                "db_user", "sys_user", "UserDeleted", userId);
    }

    private Map<String, Object> buildUserData(SysUser user) {
        return buildData(
                "username", user.getUsername(),
                "realName", user.getRealName(),
                "phone", user.getPhone(),
                "email", user.getEmail(),
                "status", user.getStatus(),
                "deptId", user.getDeptId()
        );
    }

    public void publishDeptCreated(SysDept dept) {
        publishEvent(DataSyncEvent.ofInsert("Dept", dept.getId().toString(), buildDeptData(dept)),
                "db_org", "sys_dept", "DeptCreated", dept.getId());
    }

    public void publishDeptUpdated(SysDept dept) {
        publishEvent(DataSyncEvent.create("Dept", dept.getId().toString(), DataSyncEventType.UPDATE,
                        buildDeptData(dept)),
                "db_org", "sys_dept", "DeptUpdated", dept.getId());
    }

    public void publishDeptDeleted(UUID deptId) {
        publishEvent(DataSyncEvent.ofDelete("Dept", deptId.toString()),
                "db_org", "sys_dept", "DeptDeleted", deptId);
    }

    private Map<String, Object> buildDeptData(SysDept dept) {
        return buildData(
                "deptCode", dept.getDeptCode(),
                "deptName", dept.getDeptName(),
                "leaderId", dept.getLeaderId(),
                "status", dept.getStatus()
        );
    }

    public void publishRoleCreated(SysRole role) {
        publishEvent(DataSyncEvent.ofInsert("Role", role.getId().toString(), buildRoleData(role)),
                "db_permission", "sys_role", "RoleCreated", role.getId());
    }

    public void publishRoleUpdated(SysRole role) {
        publishEvent(DataSyncEvent.create("Role", role.getId().toString(), DataSyncEventType.UPDATE,
                        buildRoleData(role)),
                "db_permission", "sys_role", "RoleUpdated", role.getId());
    }

    public void publishRoleDeleted(UUID roleId) {
        publishEvent(DataSyncEvent.ofDelete("Role", roleId.toString()),
                "db_permission", "sys_role", "RoleDeleted", roleId);
    }

    private Map<String, Object> buildRoleData(SysRole role) {
        return buildData(
                "roleCode", role.getRoleCode(),
                "roleName", role.getRoleName(),
                "status", role.getStatus()
        );
    }

    private void publishEvent(DataSyncEvent event, String database, String table, String action, Object id) {
        event.setSourceDatabase(database);
        event.setSourceTable(table);
        publisher.publishAsync(event);
        log.debug("[DataSync] Published {}: id={}", action, id);
    }

    private Map<String, Object> buildData(Object... keyValues) {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            data.put((String) keyValues[i], keyValues[i + 1]);
        }
        return data;
    }
}
