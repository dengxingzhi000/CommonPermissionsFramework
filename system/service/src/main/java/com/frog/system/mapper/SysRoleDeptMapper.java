package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysRoleDept;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * 角色部门关联 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-15
 */
@Mapper
@DS("permission")
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {

    /**
     * 根据角色 ID查询部门ID列表
     */
    @Select("""
            SELECT dept_id FROM sys_role_dept
            WHERE role_id = #{roleId}
            """)
    List<UUID> findDeptIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据角色ID查询关联信息（包含子部门标记）
     */
    @Select("""
            SELECT * FROM sys_role_dept
            WHERE role_id = #{roleId}
            """)
    List<SysRoleDept> findByRoleId(@Param("roleId") UUID roleId);

    // 注意：findAccessibleDeptIds 方法已移至 Service 层实现
    // 该方法涉及跨库查询（permission + org），需要通过 Service 层聚合：
    // 1. 先通过 findByRoleId 查询角色部门关联（包含 include_children 标记）
    // 2. 对于 include_children=true 的部门，通过 SysDeptMapper.selectDeptAndChildren 递归查询
    // 3. 合并所有部门 ID

    /**
     * 查询需要递归子部门的部门 ID 列表
     */
    @Select("""
            SELECT dept_id FROM sys_role_dept
            WHERE role_id = #{roleId} AND include_children = true
            """)
    List<UUID> findDeptIdsWithChildren(@Param("roleId") UUID roleId);

    /**
     * 查询不需要递归子部门的部门 ID 列表
     */
    @Select("""
            SELECT dept_id FROM sys_role_dept
            WHERE role_id = #{roleId} AND include_children = false
            """)
    List<UUID> findDeptIdsWithoutChildren(@Param("roleId") UUID roleId);

    /**
     * 删除角色的所有部门关联
     */
    @Delete("""
            DELETE FROM sys_role_dept
            WHERE role_id = #{roleId}
            """)
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 删除部门的所有角色关联
     * <p>
     * 用于部门删除时清理关联数据
     */
    @Delete("""
            DELETE FROM sys_role_dept
            WHERE dept_id = #{deptId}
            """)
    int deleteByDeptId(@Param("deptId") UUID deptId);

    /**
     * 批量插入角色部门关联
     */
    @Insert("""
            <script>
            INSERT INTO sys_role_dept (id, role_id, dept_id, include_children, create_by, create_time) VALUES
            <foreach collection='deptIds' item='deptId' separator=','>
            (gen_random_uuid(), #{roleId}, #{deptId}, #{includeChildren}, #{createBy}, NOW())
            </foreach>
            </script>
            """)
    int batchInsert(@Param("roleId") UUID roleId,
                    @Param("deptIds") List<UUID> deptIds,
                    @Param("includeChildren") boolean includeChildren,
                    @Param("createBy") UUID createBy);

    /**
     * 删除角色部门关联
     * <p>
     * 用于删除角色时清理自定义数据权限关联
     */
    @Delete("""
            DELETE FROM sys_role_dept
            WHERE role_id = #{roleId}
            """)
    int deleteRoleDepts(@Param("roleId") UUID roleId);
}
