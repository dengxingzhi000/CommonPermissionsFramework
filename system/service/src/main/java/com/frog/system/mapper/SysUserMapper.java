package com.frog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUser;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户表 Mapper 接口 2.0
 *
 * @author Deng
 * @since 2025-11-03
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT * FROM sys_user
            WHERE username = #{username} AND deleted = 0
            """)
    SysUser findByUsername(@Param("username") String username);

    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user 
            WHERE username = #{username} AND deleted = 0
            """)
    boolean existsByUsername(@Param("username") String username);

    @Select("""
            SELECT r.role_code FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND r.deleted = 0
            AND ur.approval_status = 1
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Set<String> findRolesByUserId(@Param("userId") UUID userId);

    @Select("""
            SELECT DISTINCT p.permission_code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
            INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND p.status = 1 AND p.deleted = 0
            AND ur.approval_status = 1
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Set<String> findEffectivePermissionsByUserId(@Param("userId") UUID userId);

    /**
     * DEPRECATED: Use findUserRolesWithNames instead to avoid N+1 query
     * @deprecated This method causes N+1 queries when used with findRoleNamesByUserId
     */
    @Deprecated
    @Select("""
            SELECT role_id FROM sys_user_role
            WHERE user_id = #{userId}
            AND (expire_time IS NULL OR expire_time > NOW())
            """)
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

    /**
     * DEPRECATED: Use findUserRolesWithNames instead to avoid N+1 query
     * @deprecated This method causes N+1 queries when used with findRoleIdsByUserId
     */
    @Deprecated
    @Select("""
            SELECT r.role_name FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.deleted = 0
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    List<String> findRoleNamesByUserId(@Param("userId") UUID userId);

    /**
     * Efficient query to fetch both role IDs and names in a single query
     * Replaces the N+1 pattern of calling findRoleIdsByUserId + findRoleNamesByUserId
     *
     * @param userId User ID
     * @return Map with keys: "id" (UUID), "name" (String)
     */
    @Select("""
            SELECT ur.role_id as id, r.role_name as name
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            AND r.deleted = 0
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    List<Map<String, Object>> findUserRolesWithNames(@Param("userId") UUID userId);

    @Update("""
            UPDATE sys_user SET 
                last_login_time = #{loginTime},
                last_login_ip = #{ipAddress}, 
                login_attempts = 0
            WHERE id = #{userId}
            """)
    void updateLastLogin(@Param("userId") UUID userId,
                         @Param("ipAddress") String ipAddress,
                         @Param("loginTime") LocalDateTime loginTime);

    @Update("""
            UPDATE sys_user SET login_attempts = login_attempts + 1
            WHERE username = #{username}
            """)
    void incrementLoginAttempts(@Param("username") String username);

    @Update("""
            UPDATE sys_user 
            SET status = 2, 
                locked_until = #{lockedUntil}
            WHERE username = #{username}
            """)
    void lockAccount(@Param("username") String username,
                     @Param("lockedUntil") LocalDateTime lockedUntil);

    @Delete("""
            DELETE FROM sys_user_role 
            WHERE user_id = #{userId}
            """)
    void deleteUserRoles(@Param("userId") UUID userId);

    /**
     * 批量插入用户角色（永久授权）
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role 
            (id, user_id, role_id, approval_status, create_by, create_time) 
            VALUES
            <foreach collection='roleIds' item='roleId' separator=','>
            (UNHEX(REPLACE(UUID(), '-', '')), #{userId}, #{roleId}, 1, #{createBy}, NOW())
            </foreach>
            </script>
            """)
    void batchInsertUserRoles(@Param("userId") UUID userId,
                              @Param("roleIds") List<UUID> roleIds,
                              @Param("createBy") UUID createBy);

    /**
     * 批量插入用户角色（临时授权，带过期时间）
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role 
            (id, user_id, role_id, approval_status, effective_time, expire_time, create_by, create_time) 
            VALUES
            <foreach collection='roleIds' item='roleId' separator=','>
            (UNHEX(REPLACE(UUID(), '-', '')), 
             #{userId}, #{roleId}, 1, 
             #{effectiveTime}, #{expireTime}, 
             #{createBy}, NOW())
            </foreach>
            </script>
            """)
    void batchInsertTemporaryUserRoles(
            @Param("userId") UUID userId,
            @Param("roleIds") List<UUID> roleIds,
            @Param("effectiveTime") LocalDateTime effectiveTime,
            @Param("expireTime") LocalDateTime expireTime,
            @Param("createBy") UUID createBy);

    @Select("""
            SELECT MIN(r.data_scope) FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND r.deleted = 0
            AND ur.approval_status = 1
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Integer getUserDataScope(@Param("userId") UUID userId);

    @Select("""
            SELECT MAX(r.max_approval_amount) FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND r.deleted = 0
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    BigDecimal getMaxApprovalAmount(@Param("userId") UUID userId);

    @Select("""
            WITH RECURSIVE dept_tree AS (
                SELECT id, parent_id FROM sys_dept 
                WHERE id = (SELECT dept_id FROM sys_user WHERE id = #{userId})
                UNION ALL
                SELECT d.id, d.parent_id FROM sys_dept d
                INNER JOIN dept_tree dt ON d.parent_id = dt.id
            )
            SELECT id FROM dept_tree
            """)
    List<UUID> findUserDeptAndChildren(@Param("userId") UUID userId);

    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE u.id = #{userId}
            AND (
                r.data_scope = 1  -- 全部数据
                OR (r.data_scope = 3 AND u.dept_id = #{deptId})  -- 本部门
                OR (r.data_scope = 4 AND #{deptId} IN (
                    WITH RECURSIVE dept_tree AS (
                        SELECT id FROM sys_dept WHERE id = u.dept_id
                        UNION ALL
                        SELECT d.id FROM sys_dept d
                        INNER JOIN dept_tree dt ON d.parent_id = dt.id
                    )
                    SELECT id FROM dept_tree
                ))  -- 本部门及子部门
            )
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    boolean hasAccessToDept(@Param("userId") UUID userId, @Param("deptId") UUID deptId);

    @Select("""
            SELECT u.id as user_id, u.username, u.email, 
                   r.role_name, ur.expire_time
            FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.expire_time IS NOT NULL
            AND ur.expire_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL #{days} DAY)
            AND ur.approval_status = 1
            """)
    List<Map<String, Object>> findExpiringRoles(@Param("days") Integer days);

    @Select("""
            SELECT u.id as user_id, u.username, r.role_name, ur.expire_time
            FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.expire_time < NOW()
            AND ur.approval_status = 1
            """)
    List<Map<String, Object>> findExpiredRoles();

    @Delete("""
            DELETE FROM sys_user_role 
            WHERE expire_time < NOW()
            AND approval_status = 1
            """)
    int deleteExpiredRoles();

    @Update("""
            UPDATE sys_user_role 
            SET approval_status = 2
            WHERE expire_time < NOW()
            AND approval_status = 1
            """)
    int updateExpiredRolesStatus();

    /**
     * 查询用户的临时授权列表
     */
    @Select("""
            SELECT ur.id, ur.role_id, r.role_name, 
                   ur.effective_time, ur.expire_time,
                   ur.approval_status, ur.create_time
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            AND ur.expire_time IS NOT NULL
            ORDER BY ur.create_time DESC
            """)
    List<Map<String, Object>> findTemporaryRolesByUserId(@Param("userId") UUID userId);

    /**
     * 检查用户是否有特定的临时角色
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user_role
            WHERE user_id = #{userId}
            AND role_id = #{roleId}
            AND expire_time IS NOT NULL
            AND expire_time > NOW()
            AND approval_status = 1
            """)
    boolean hasTemporaryRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 延长临时角色的过期时间
     */
    @Update("""
            UPDATE sys_user_role
            SET expire_time = #{newExpireTime}
            WHERE user_id = #{userId}
            AND role_id = #{roleId}
            AND expire_time IS NOT NULL
            AND expire_time > NOW()
            """)
    int extendTemporaryRole(
            @Param("userId") UUID userId,
            @Param("roleId") UUID roleId,
            @Param("newExpireTime") LocalDateTime newExpireTime);

    /**
     * 提前终止临时授权
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = 0,
                expire_time = NOW()
            WHERE user_id = #{userId}
            AND role_id = #{roleId}
            AND expire_time IS NOT NULL
            AND expire_time > NOW()
            """)
    int terminateTemporaryRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 查询用户待审批的角色申请
     */
    @Select("""
            SELECT ur.id, ur.role_id, r.role_name,
                   ur.effective_time, ur.expire_time,
                   ur.create_time
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            AND ur.approval_status = 0
            ORDER BY ur.create_time DESC
            """)
    List<Map<String, Object>> findPendingRoleApprovals(@Param("userId") UUID userId);

    /**
     * 更新角色审批状态
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = #{status},
                approved_by = #{approvedBy},
                approved_time = NOW()
            WHERE id = #{id}
            """)
    int updateRoleApprovalStatus(
            @Param("id") UUID id,
            @Param("status") Integer status,
            @Param("approvedBy") UUID approvedBy);

    /**
     * 统计用户的角色数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE user_id = #{userId}
            AND approval_status = 1
            AND (expire_time IS NULL OR expire_time > NOW())
            """)
    Integer countUserRoles(@Param("userId") UUID userId);

    /**
     * 统计用户的临时角色数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE user_id = #{userId}
            AND approval_status = 1
            AND expire_time IS NOT NULL
            AND expire_time > NOW()
            """)
    Integer countTemporaryRoles(@Param("userId") UUID userId);

    /**
     * 统计即将过期的临时角色数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE user_id = #{userId}
            AND expire_time IS NOT NULL
            AND expire_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL #{days} DAY)
            """)
    Integer countExpiringRoles(@Param("userId") UUID userId, @Param("days") Integer days);
}