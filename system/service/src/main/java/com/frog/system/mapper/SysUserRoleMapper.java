package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUserRole;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户角色关联 Mapper 接口
 * <p>
 * 处理 db_permission 库中的 sys_user_role、sys_role、sys_permission 等表
 *
 * @author Deng
 * @since 2025-12-15
 */
@Mapper
@DS("permission")
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询用户的有效角色 ID 列表
     */
    @Select("""
            SELECT role_id FROM sys_user_role
            WHERE user_id = #{userId}
              AND approval_status = 2
              AND (expire_time IS NULL OR expire_time > NOW())
            """)
    List<UUID> findEffectiveRoleIds(@Param("userId") UUID userId);

    /**
     * 查询用户的有效角色编码列表
     */
    @Select("""
            SELECT r.role_code FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND NOT r.deleted
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Set<String> findRoleCodesByUserId(@Param("userId") UUID userId);

    /**
     * 查询用户的有效角色（包含 ID 和名称）
     */
    @Select("""
            SELECT ur.role_id as id, r.role_code as code, r.role_name as name
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            AND NOT r.deleted
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    List<Map<String, Object>> findUserRolesWithNames(@Param("userId") UUID userId);

    /**
     * 查询用户的所有角色关联（包括过期和待审批的）
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE user_id = #{userId}
            """)
    List<SysUserRole> findByUserId(@Param("userId") UUID userId);

    /**
     * 查询拥有指定角色的用户 ID 列表
     */
    @Select("""
            SELECT user_id FROM sys_user_role
            WHERE role_id = #{roleId}
              AND approval_status = 2
              AND (expire_time IS NULL OR expire_time > NOW())
            """)
    List<UUID> findUserIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 检查用户是否拥有指定角色（有效的）
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user_role
            WHERE user_id = #{userId}
              AND role_id = #{roleId}
              AND approval_status = 2
              AND (expire_time IS NULL OR expire_time > NOW())
            """)
    boolean hasRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 查询用户的有效权限编码列表
     */
    @Select("""
            SELECT DISTINCT p.permission_code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
            INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND p.status = 1 AND NOT p.deleted
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Set<String> findPermissionCodesByUserId(@Param("userId") UUID userId);

    /**
     * 获取用户的数据权限范围（取最小值，即最大权限）
     */
    @Select("""
            SELECT MIN(r.data_scope) FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND NOT r.deleted
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    Integer getUserDataScope(@Param("userId") UUID userId);

    /**
     * 获取用户的最大审批金额
     */
    @Select("""
            SELECT MAX(r.max_approval_amount) FROM sys_role r
            INNER JOIN sys_user_role ur ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            AND r.status = 1 AND NOT r.deleted
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            """)
    BigDecimal getMaxApprovalAmount(@Param("userId") UUID userId);

    /**
     * 查询用户即将过期的角色（7天内）
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE user_id = #{userId}
              AND approval_status = 2
              AND expire_time IS NOT NULL
              AND expire_time BETWEEN NOW() AND NOW() + INTERVAL '7 days'
            """)
    List<SysUserRole> findExpiringRolesByUserId(@Param("userId") UUID userId);

    /**
     * 查询所有已过期的角色关联
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE approval_status = 2
              AND expire_time IS NOT NULL
              AND expire_time < NOW()
            """)
    List<SysUserRole> findAllExpiredRoles();

    /**
     * 查询用户的临时授权列表（包含角色名称）
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
            AND approval_status = 2
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
    int extendTemporaryRole(@Param("userId") UUID userId,
                            @Param("roleId") UUID roleId,
                            @Param("newExpireTime") LocalDateTime newExpireTime);

    /**
     * 提前终止临时授权
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = 3,
                expire_time = NOW()
            WHERE user_id = #{userId}
            AND role_id = #{roleId}
            AND expire_time IS NOT NULL
            AND expire_time > NOW()
            """)
    int terminateTemporaryRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 查询即将过期的角色（用于提醒，返回用户和角色信息）
     */
    @Select("""
            SELECT DISTINCT ur.user_id, ur.username, ur.role_id, r.role_name, ur.expire_time
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.expire_time IS NOT NULL
            AND ur.expire_time BETWEEN NOW() AND NOW() + make_interval(days => #{days})
            AND ur.approval_status = 2
            """)
    List<Map<String, Object>> findExpiringRolesForNotification(@Param("days") Integer days);

    /**
     * 查询已过期的角色（用于清理，包含用户信息）
     */
    @Select("""
            SELECT DISTINCT ur.user_id, ur.username, ur.role_id, r.role_name, ur.expire_time
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.expire_time < NOW()
            AND ur.approval_status = 2
            """)
    List<Map<String, Object>> findExpiredRolesForCleanup();

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
     * 更新审批状态
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = #{status},
                approved_by = #{approvedBy},
                approved_time = NOW()
            WHERE id = #{id}
            """)
    int updateApprovalStatus(@Param("id") UUID id,
                             @Param("status") int status,
                             @Param("approvedBy") UUID approvedBy);

    /**
     * 删除用户的所有角色关联
     */
    @Delete("""
            DELETE FROM sys_user_role
            WHERE user_id = #{userId}
            """)
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * 删除角色的所有用户关联
     */
    @Delete("""
            DELETE FROM sys_user_role
            WHERE role_id = #{roleId}
            """)
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 删除指定的用户角色关联
     */
    @Delete("""
            DELETE FROM sys_user_role
            WHERE user_id = #{userId} AND role_id = #{roleId}
            """)
    int deleteByUserIdAndRoleId(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /**
     * 批量插入用户角色关联（永久授权）
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role (id, user_id, role_id, approval_status, create_by, create_time) VALUES
            <foreach collection='roleIds' item='roleId' separator=','>
            (gen_random_uuid(), #{userId}, #{roleId}, 2, #{createBy}, NOW())
            </foreach>
            </script>
            """)
    int batchInsert(@Param("userId") UUID userId,
                    @Param("roleIds") List<UUID> roleIds,
                    @Param("createBy") UUID createBy);

    /**
     * 批量插入临时用户角色关联
     */
    @Insert("""
            <script>
            INSERT INTO sys_user_role
            (id, user_id, role_id, approval_status, effective_time, expire_time, create_by, create_time)
            VALUES
            <foreach collection='roleIds' item='roleId' separator=','>
            (gen_random_uuid(), #{userId}, #{roleId}, 2, #{effectiveTime}, #{expireTime}, #{createBy}, NOW())
            </foreach>
            </script>
            """)
    int batchInsertTemporary(@Param("userId") UUID userId,
                             @Param("roleIds") List<UUID> roleIds,
                             @Param("effectiveTime") LocalDateTime effectiveTime,
                             @Param("expireTime") LocalDateTime expireTime,
                             @Param("createBy") UUID createBy);

    /**
     * 插入临时角色授权
     */
    @Insert("""
            INSERT INTO sys_user_role (id, user_id, role_id, effective_time, expire_time, approval_status, approved_by, approved_time, create_by, create_time)
            VALUES (gen_random_uuid(), #{userId}, #{roleId}, #{effectiveTime}, #{expireTime}, #{approvalStatus}, #{approvedBy}, #{approvedTime}, #{createBy}, NOW())
            """)
    int insertTemporary(SysUserRole userRole);

    // ==================== 过期清理 ====================

    /**
     * 删除已过期的角色关联
     */
    @Delete("""
            DELETE FROM sys_user_role
            WHERE expire_time < NOW()
            AND approval_status = 2
            """)
    int deleteExpiredRoles();

    /**
     * 更新过期角色状态为已拒绝
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = 3
            WHERE expire_time < NOW()
            AND approval_status = 2
            """)
    int updateExpiredRolesStatus();

    // ==================== 统计查询 ====================

    /**
     * 统计用户的有效角色数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE user_id = #{userId}
            AND approval_status = 2
            AND (expire_time IS NULL OR expire_time > NOW())
            """)
    Integer countUserRoles(@Param("userId") UUID userId);

    /**
     * 统计用户的临时角色数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE user_id = #{userId}
            AND approval_status = 2
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
            AND approval_status = 2
            AND expire_time IS NOT NULL
            AND expire_time BETWEEN NOW() AND NOW() + make_interval(days => #{days})
            """)
    Integer countExpiringRoles(@Param("userId") UUID userId, @Param("days") Integer days);

    // ==================== 冗余字段同步（数据一致性） ====================

    /**
     * 更新用户冗余信息
     * 当 db_user.sys_user 变更时调用
     */
    @Update("""
            UPDATE sys_user_role
            SET username = #{username},
                real_name = #{realName},
                user_status = #{status}
            WHERE user_id = #{userId}
            """)
    int updateUserRedundancy(@Param("userId") UUID userId,
                             @Param("username") String username,
                             @Param("realName") String realName,
                             @Param("status") Integer status);

    /**
     * 更新用户状态冗余字段
     */
    @Update("""
            UPDATE sys_user_role
            SET user_status = #{status}
            WHERE user_id = #{userId}
            """)
    int updateUserStatus(@Param("userId") UUID userId, @Param("status") Integer status);

    /**
     * 获取所有不重复的用户ID（用于初始化同步）
     */
    @Select("""
            SELECT DISTINCT user_id FROM sys_user_role
            """)
    List<UUID> findAllDistinctUserIds();

    /**
     * 根据用户名查询用户角色（利用冗余字段，无需跨库）
     */
    @Select("""
            SELECT ur.*, r.role_code, r.role_name
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.username = #{username}
            AND ur.user_status = 1
            AND ur.approval_status = 2
            AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
            AND NOT r.deleted
            """)
    List<Map<String, Object>> findRolesByUsername(@Param("username") String username);

    /**
     * 统计拥有该角色的用户数
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE role_id = #{roleId}
            """)
    Integer countUsersByRoleId(@Param("roleId") UUID roleId);
}
