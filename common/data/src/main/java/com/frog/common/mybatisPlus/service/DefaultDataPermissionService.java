package com.frog.common.mybatisPlus.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 默认数据权限服务实现
 * 从数据库查询用户的自定义数据权限规则
 *
 * @author Deng
 * @since 2025-12-15
 */
@Service
@Slf4j
@ConditionalOnMissingBean(DataPermissionService.class)
@RequiredArgsConstructor
public class DefaultDataPermissionService implements DataPermissionService {
    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询用户的自定义数据权限部门列表
     * SQL: 通过用户ID -> 用户角色 -> 角色部门关联 获取可访问部门
     */
    @Override
    public List<UUID> findCustomDeptPermissions(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        try {
            String sql = """
                SELECT DISTINCT rd.dept_id
                FROM sys_role_dept rd
                INNER JOIN sys_user_role ur ON rd.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND ur.approval_status = 2
                  AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Object obj = rs.getObject("dept_id");
                if (obj instanceof UUID) {
                    return (UUID) obj;
                }
                return UUID.fromString(rs.getString("dept_id"));
            }, userId);

        } catch (Exception e) {
            log.warn("Failed to query custom data permissions for user {}: {}",
                    userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 检查用户是否有自定义数据权限配置
     */
    @Override
    public boolean hasCustomDataPermission(UUID userId) {
        if (userId == null) {
            return false;
        }

        try {
            String sql = """
                SELECT COUNT(*) > 0
                FROM sys_role_dept rd
                INNER JOIN sys_user_role ur ON rd.role_id = ur.role_id
                WHERE ur.user_id = ?
                  AND ur.approval_status = 2
                  AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
                """;

            Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class, userId);
            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.warn("Failed to check custom data permission for user {}: {}",
                    userId, e.getMessage());
            return false;
        }
    }
}
