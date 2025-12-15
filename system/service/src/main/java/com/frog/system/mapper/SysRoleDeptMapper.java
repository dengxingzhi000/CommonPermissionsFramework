package com.frog.system.mapper;

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
public interface SysRoleDeptMapper extends BaseMapper<SysRoleDept> {

    /**
     * 根据角色ID查询部门ID列表
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

    /**
     * 根据角色ID获取可访问的部门ID（递归包含子部门）
     */
    @Select("""
            WITH RECURSIVE dept_tree AS (
                SELECT d.id
                FROM sys_role_dept rd
                JOIN sys_dept d ON rd.dept_id = d.id
                WHERE rd.role_id = #{roleId}

                UNION ALL

                SELECT child.id
                FROM sys_dept child
                JOIN dept_tree parent ON child.parent_id = parent.id
                JOIN sys_role_dept rd ON rd.role_id = #{roleId} AND rd.include_children = true
                WHERE NOT child.deleted
            )
            SELECT DISTINCT id FROM dept_tree
            """)
    List<UUID> findAccessibleDeptIds(@Param("roleId") UUID roleId);

    /**
     * 删除角色的所有部门关联
     */
    @Delete("""
            DELETE FROM sys_role_dept
            WHERE role_id = #{roleId}
            """)
    int deleteByRoleId(@Param("roleId") UUID roleId);

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
}
