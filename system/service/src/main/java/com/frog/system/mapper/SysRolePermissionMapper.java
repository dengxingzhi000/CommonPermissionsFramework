package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysRolePermission;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * 角色权限关联表 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-17
 */
@Mapper
@DS("permission")
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 根据角色 ID 查询权限 ID 列表
     */
    @Select("""
            SELECT permission_id FROM sys_role_permission
            WHERE role_id = #{roleId}
            """)
    List<UUID> findPermissionIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据权限 ID 查询角色 ID 列表
     */
    @Select("""
            SELECT role_id FROM sys_role_permission
            WHERE permission_id = #{permissionId}
            """)
    List<UUID> findRoleIdsByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * 删除角色的所有权限关联
     */
    @Delete("""
            DELETE FROM sys_role_permission
            WHERE role_id = #{roleId}
            """)
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 删除权限的所有角色关联
     */
    @Delete("""
            DELETE FROM sys_role_permission
            WHERE permission_id = #{permissionId}
            """)
    int deleteByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * 检查角色是否拥有指定权限
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_role_permission
            WHERE role_id = #{roleId} AND permission_id = #{permissionId}
            """)
    boolean existsByRoleIdAndPermissionId(@Param("roleId") UUID roleId,
                                          @Param("permissionId") UUID permissionId);

    /**
     * 删除角色权限关联
     */
    @Delete("""
            DELETE FROM sys_role_permission
            WHERE role_id = #{roleId}
            """)
    void deleteRolePermissions(@Param("roleId") UUID roleId);

    /**
     * 批量插入角色权限
     */
    @Insert("""
            <script>
            INSERT INTO sys_role_permission (role_id, permission_id, create_by, create_time) VALUES
            <foreach collection='permissionIds' item='permissionId' separator=','>
            (#{roleId}, #{permissionId}, #{createBy}, NOW())
            </foreach>
            </script>
            """)
    void batchInsertRolePermissions(@Param("roleId") UUID roleId,
                                    @Param("permissionIds") List<UUID> permissionIds,
                                    @Param("createBy") UUID createBy);

    /**
     * 统计使用该权限的角色数
     */
    @Select("""
            SELECT COUNT(*) FROM sys_role_permission WHERE permission_id = #{permissionId}
            """)
    Integer countRolesByPermissionId(@Param("permissionId") UUID permissionId);
}