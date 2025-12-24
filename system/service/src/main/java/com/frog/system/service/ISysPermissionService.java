package com.frog.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.frog.common.dto.permission.ApiPermissionDTO;
import com.frog.common.dto.permission.PermissionDTO;
import com.frog.system.domain.entity.SysPermission;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * <p>
 * 权限表 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-14
 */
public interface ISysPermissionService extends IService<SysPermission> {

    /**
     * 判断用户是否拥有指定权限编码。
     *
     * @param userId 用户 ID
     * @param permissionCode 权限编码
     * @return true 表示拥有该权限；false 表示不拥有
     */
    boolean hasPermission(UUID userId, String permissionCode);

    /**
     * 判断用户是否对指定资源拥有某项权限。
     *
     * @param userId 用户 ID
     * @param resourceType 资源类型（如：PROJECT、DEPT 等）
     * @param resourceId 资源ID（可序列化）
     * @param permission 权限动作（如：READ、WRITE、DELETE 等）
     * @return true 表示拥有该资源权限；false 表示不拥有
     */
    boolean hasResourcePermission(UUID userId, String resourceType,
                                  Serializable resourceId, String permission);

    /**
     * 查询用户所拥有的角色编码集合。
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    Set<String> getUserRoles(UUID userId);

    /**
     * 查询用户所拥有的权限编码集合。
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    Set<String> getUserPermissions(UUID userId);

    /**
     * 获取权限树（树形结构）。
     *
     * @return 权限树列表
     */
    List<PermissionDTO> getPermissionTree();

    /**
     * 根据ID获取权限详情。
     *
     * @param id 权限 ID
     * @return 权限详情
     */
    PermissionDTO getPermissionById(UUID id);

    /**
     * 根据 API 路径与 HTTP 方法匹配所需的权限编码列表。
     *
     * @param url 请求路径（Path）
     * @param method HTTP 方法（如：GET、POST、PUT、DELETE）
     * @return 访问该接口所需的权限编码列表
     */
    List<String> findPermissionsByUrl(String url, String method);

    /**
     * 查询所有 API 类型的权限。
     * 用于动态权限加载（DynamicPermissionLoader）。
     *
     * @return API 权限列表，包含路径、HTTP 方法和权限编码
     */
    List<ApiPermissionDTO> findApiPermissions();

    /**
     * 新增权限。
     *
     * @param permissionDTO 权限信息
     */
    void addPermission(PermissionDTO permissionDTO);

    /**
     * 修改权限。
     *
     * @param permissionDTO 权限信息
     */
    void updatePermission(PermissionDTO permissionDTO);

    /**
     * 删除权限。
     *
     * @param id 权限 ID
     */
    void deletePermission(UUID id);
}
