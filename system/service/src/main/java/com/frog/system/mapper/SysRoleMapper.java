package com.frog.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 角色表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 检查角色编码是否存在
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_role
            WHERE role_code = #{roleCode} AND deleted = 0
            """)
    boolean existsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 查询角色权限ID列表
     */
    @Select("""
            SELECT permission_id FROM sys_role_permission
            WHERE role_id = #{roleId}
            """)
    List<UUID> findPermissionIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 统计拥有该角色的用户数
     */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role
            WHERE role_id = #{roleId}
            """)
    Integer countUsersByRoleId(@Param("roleId") UUID roleId);

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
}
