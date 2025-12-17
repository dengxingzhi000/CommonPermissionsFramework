package com.frog.system.sync.handler;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.frog.common.integration.sync.event.DataSyncEvent;
import com.frog.common.integration.sync.event.DataSyncEventType;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import com.frog.common.integration.sync.reconciliation.DataReconciliationTask;
import com.frog.system.domain.entity.SysUser;
import com.frog.system.mapper.SysDeptMapper;
import com.frog.system.mapper.SysUserMapper;
import com.frog.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户数据同步处理器
 * <p>
 * 处理用户数据变更，同步更新冗余字段到其他库：
 * - db_permission.sys_user_role (username, real_name, user_status)
 * - db_org.sys_dept (leader_name, leader_phone)
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSyncHandler implements DataSyncHandler,
        DataReconciliationTask.ReconcilableHandler {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;

    @Override
    public String getAggregateType() {
        return "User";
    }

    @Override
    public void handle(DataSyncEvent event) throws DataSyncException {
        UUID userId = UUID.fromString(event.getPrimaryId());
        Map<String, Object> data = event.getAfterData();

        log.debug("[UserSync] Handling event: userId={}, type={}",
                userId, event.getEventType());

        try {
            switch (event.getEventType()) {
                case INSERT, UPDATE -> {
                    syncToPermissionDb(userId, data);
                    syncToOrgDb(userId, data);
                }
                case DELETE -> markDeletedInPermissionDb(userId);
                default -> log.warn("[UserSync] Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            throw new DataSyncException("Failed to sync user: " + userId, e, true);
        }
    }

    @Override
    public void fullSync(String primaryId) {
        UUID userId = UUID.fromString(primaryId);
        SysUser user = userMapper.selectById(userId);
        if (user != null) {
            Map<String, Object> data = buildUserData(user);
            syncToPermissionDb(userId, data);
            syncToOrgDb(userId, data);
        }
    }

    /**
     * 同步用户信息到 permission 库
     */
    @DS("permission")
    @Transactional
    public void syncToPermissionDb(UUID userId, Map<String, Object> data) {
        String username = (String) data.get("username");
        String realName = (String) data.get("realName");
        Integer status = (Integer) data.get("status");

        int updated = userRoleMapper.updateUserRedundancy(userId, username, realName, status);
        log.debug("[UserSync] Updated {} rows in sys_user_role for user: {}", updated, userId);
    }

    /**
     * 同步用户信息到 org 库（负责人信息）
     */
    @DS("org")
    @Transactional
    public void syncToOrgDb(UUID userId, Map<String, Object> data) {
        String realName = (String) data.get("realName");
        String phone = (String) data.get("phone");

        int updated = deptMapper.updateLeaderRedundancy(userId, realName, phone);
        log.debug("[UserSync] Updated {} rows in sys_dept for leader: {}", updated, userId);
    }

    @DS("permission")
    @Transactional
    public void markDeletedInPermissionDb(UUID userId) {
        userRoleMapper.updateUserStatus(userId, 0);
        log.debug("[UserSync] Marked user as deleted in permission db: {}", userId);
    }

    private Map<String, Object> buildUserData(SysUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("phone", user.getPhone());
        data.put("email", user.getEmail());
        data.put("status", user.getStatus());
        data.put("deptId", user.getDeptId());
        return data;
    }

    // ==================== 对账实现 ====================

    @Override
    public DataReconciliationTask.ReconciliationReport reconcile(int batchSize, boolean autoFix) {
        log.info("[UserSync] Starting reconciliation, batchSize={}, autoFix={}", batchSize, autoFix);

        int totalChecked = 0;
        int inconsistentCount = 0;
        int fixedCount = 0;
        int failedCount = 0;

        // 1. 获取所有有角色关联的用户 ID
        List<UUID> userIds = userRoleMapper.findAllDistinctUserIds();

        for (int i = 0; i < userIds.size(); i += batchSize) {
            List<UUID> batch = userIds.subList(i, Math.min(i + batchSize, userIds.size()));

            // 2. 从 user 库获取用户信息
            List<SysUser> users = userMapper.selectBasicInfoByIds(batch);
            Map<UUID, SysUser> userMap = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

            // 3. 检查每个用户的冗余数据
            for (UUID userId : batch) {
                totalChecked++;
                SysUser user = userMap.get(userId);

                if (user == null) {
                    // 用户已删除，但角色关联还在
                    inconsistentCount++;
                    if (autoFix) {
                        try {
                            markDeletedInPermissionDb(userId);
                            fixedCount++;
                        } catch (Exception e) {
                            failedCount++;
                            log.error("[UserSync] Failed to fix: userId={}", userId, e);
                        }
                    }
                } else if (autoFix) {
                    // 强制同步确保数据一致
                    try {
                        fullSync(userId.toString());
                        fixedCount++;
                    } catch (Exception e) {
                        failedCount++;
                    }
                }
            }
        }

        return new DataReconciliationTask.ReconciliationReport(
                totalChecked, inconsistentCount, fixedCount, failedCount);
    }
}
