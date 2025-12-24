package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
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
@DS("permission")
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 检查角色编码是否存在
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_role
            WHERE role_code = #{roleCode} AND NOT deleted
            """)
    boolean existsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 根据角色编码查询角色 ID
     */
    @Select("""
            SELECT id FROM sys_role
            WHERE role_code = #{roleCode} AND status = 1 AND NOT deleted
            """)
    UUID findIdByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 根据角色编码查询角色
     */
    @Select("""
            SELECT * FROM sys_role
            WHERE role_code = #{roleCode} AND NOT deleted
            """)
    SysRole findByRoleCode(@Param("roleCode") String roleCode);
}
