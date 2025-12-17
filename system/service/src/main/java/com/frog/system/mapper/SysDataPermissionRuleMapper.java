package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysDataPermissionRule;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * 数据权限规则 Mapper 接口
 *
 * @author Deng
 * @since 2025-12-15
 */
@Mapper
@DS("permission")
public interface SysDataPermissionRuleMapper extends BaseMapper<SysDataPermissionRule> {

    /**
     * 根据规则编码查询
     */
    @Select("""
            SELECT * FROM sys_data_permission_rule
            WHERE rule_code = #{ruleCode} AND NOT deleted
            """)
    SysDataPermissionRule findByRuleCode(@Param("ruleCode") String ruleCode);

    /**
     * 根据资源类型查询启用的规则
     */
    @Select("""
            SELECT * FROM sys_data_permission_rule
            WHERE resource_type = #{resourceType} AND status = 1 AND NOT deleted
            ORDER BY priority DESC
            """)
    List<SysDataPermissionRule> findByResourceType(@Param("resourceType") String resourceType);

    /**
     * 根据角色 ID查询关联的规则
     */
    @Select("""
            SELECT r.* FROM sys_data_permission_rule r
            JOIN sys_role_data_rule rdr ON r.id = rdr.rule_id
            WHERE rdr.role_id = #{roleId} AND r.status = 1 AND NOT r.deleted
            ORDER BY r.priority DESC
            """)
    List<SysDataPermissionRule> findByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据用户ID查询关联的规则（通过用户角色）
     */
    @Select("""
            SELECT DISTINCT r.* FROM sys_data_permission_rule r
            JOIN sys_role_data_rule rdr ON r.id = rdr.rule_id
            JOIN sys_user_role ur ON rdr.role_id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND ur.approval_status = 2
              AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
              AND r.status = 1
              AND NOT r.deleted
            ORDER BY r.priority DESC
            """)
    List<SysDataPermissionRule> findByUserId(@Param("userId") UUID userId);

    /**
     * 检查规则编码是否存在
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_data_permission_rule
            WHERE rule_code = #{ruleCode} AND NOT deleted
            """)
    boolean existsByRuleCode(@Param("ruleCode") String ruleCode);
}
