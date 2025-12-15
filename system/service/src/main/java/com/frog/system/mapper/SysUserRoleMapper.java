package com.frog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysUserRole;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * 用户角色关联 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-15
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询用户的有效角色ID列表
     */
    @Select("""
            SELECT role_id FROM sys_user_role
            WHERE user_id = #{userId}
              AND approval_status = 2
              AND (expire_time IS NULL OR expire_time > NOW())
            """)
    List<UUID> findEffectiveRoleIds(@Param("userId") UUID userId);

    /**
     * 查询用户的所有角色关联（包括过期和待审批的）
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE user_id = #{userId}
            """)
    List<SysUserRole> findByUserId(@Param("userId") UUID userId);

    /**
     * 查询拥有指定角色的用户ID列表
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
     * 查询用户即将过期的角色（7天内）
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE user_id = #{userId}
              AND approval_status = 2
              AND expire_time IS NOT NULL
              AND expire_time BETWEEN NOW() AND NOW() + INTERVAL '7 days'
            """)
    List<SysUserRole> findExpiringRoles(@Param("userId") UUID userId);

    /**
     * 查询所有已过期的角色关联
     */
    @Select("""
            SELECT * FROM sys_user_role
            WHERE approval_status = 2
              AND expire_time IS NOT NULL
              AND expire_time < NOW()
            """)
    List<SysUserRole> findExpiredRoles();

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
     * 批量插入用户角色关联
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
     * 插入临时角色授权
     */
    @Insert("""
            INSERT INTO sys_user_role (id, user_id, role_id, effective_time, expire_time, approval_status, approved_by, approved_time, create_by, create_time)
            VALUES (gen_random_uuid(), #{userId}, #{roleId}, #{effectiveTime}, #{expireTime}, #{approvalStatus}, #{approvedBy}, #{approvedTime}, #{createBy}, NOW())
            """)
    int insertTemporary(SysUserRole userRole);

    /**
     * 更新审批状态
     */
    @Update("""
            UPDATE sys_user_role
            SET approval_status = #{status}, approved_by = #{approvedBy}, approved_time = NOW()
            WHERE id = #{id}
            """)
    int updateApprovalStatus(@Param("id") UUID id,
                             @Param("status") int status,
                             @Param("approvedBy") UUID approvedBy);
}
