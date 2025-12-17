package com.frog.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.frog.common.dto.role.RoleDTO;
import com.frog.system.domain.entity.SysRole;

import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-15
 */
public interface ISysRoleService extends IService<SysRole> {

    /**
     * 分页查询角色列表。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量
     * @param roleName 角色名称（可选，支持模糊查询）
     * @return 角色分页数据
     */
    Page<RoleDTO> listRoles(Integer pageNum, Integer pageSize, String roleName);

    /**
     * 查询所有角色（不分页）。
     *
     * @return 全部角色列表
     */
    List<RoleDTO> listAllRoles();

    /**
     * 根据角色ID获取角色详情。
     *
     * @param id 角色 ID
     * @return 角色详情
     */
    RoleDTO getRoleById(UUID id);

    /**
     * 新增角色。
     *
     * @param roleDTO 角色信息
     */
    void addRole(RoleDTO roleDTO);

    /**
     * 修改角色。
     *
     * @param roleDTO 角色信息
     */
    void updateRole(RoleDTO roleDTO);

    /**
     * 删除角色。
     *
     * @param id 角色 ID
     */
    void deleteRole(UUID id);

    /**
     * 为角色授予权限。
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID列表
     */
    void grantPermissions(UUID roleId, List<UUID> permissionIds);

    /**
     * 获取角色已绑定的权限ID列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID列表
     */
    List<UUID> getRolePermissionIds(UUID roleId);
}
