package com.frog.common.mybatisPlus.service;

import java.util.List;
import java.util.UUID;

/**
 * 数据权限服务接口
 * 用于查询用户的自定义数据权限规则
 *
 * @author Deng
 * @since 2025-12-15
 */
public interface DataPermissionService {

    /**
     * 查询用户的自定义数据权限部门列表
     * 从 sys_role_dept 表查询用户通过角色获得的可访问部门
     *
     * @param userId 用户 ID
     * @return 可访问的部门 ID列表
     */
    List<UUID> findCustomDeptPermissions(UUID userId);

    /**
     * 检查是否存在自定义数据权限配置
     *
     * @param userId 用户 ID
     * @return true 如果有自定义配置
     */
    boolean hasCustomDataPermission(UUID userId);
}
