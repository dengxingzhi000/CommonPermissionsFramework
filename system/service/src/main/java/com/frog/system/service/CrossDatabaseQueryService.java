package com.frog.system.service;

import com.frog.common.dto.dept.DeptDTO;
import com.frog.system.domain.entity.SysDept;
import com.frog.system.domain.entity.SysUser;
import com.frog.system.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 跨库数据聚合查询服务
 * <p>
 * 用于处理需要跨多个数据库查询的场景，在应用层进行数据聚合
 * 替代原来的跨库 JOIN 查询
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossDatabaseQueryService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleDeptMapper roleDeptMapper;

    // ==================== 用户相关跨库查询 ====================

    /**
     * 根据角色编码查询第一个有效用户 ID
     * <p>
     * 替代原 SysPermissionApprovalMapper.findFirstUserByRoleCode
     *
     * @param roleCode 角色编码
     * @return 第一个有效用户的 ID，如果没有则返回 null
     */
    public UUID findFirstUserIdByRoleCode(String roleCode) {
        // 1. 从 permission 库查询角色 ID
        UUID roleId = roleMapper.findIdByRoleCode(roleCode);
        if (roleId == null) {
            return null;
        }

        // 2. 从 permission 库查询该角色的用户 ID 列表
        List<UUID> userIds = userRoleMapper.findUserIdsByRoleId(roleId);
        if (userIds == null || userIds.isEmpty()) {
            return null;
        }

        // 3. 从 user 库查询有效用户，取第一个
        List<SysUser> users = userMapper.selectBasicInfoByIds(userIds);
        return users.stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .min(Comparator.comparing(SysUser::getCreateTime))
                .map(SysUser::getId)
                .orElse(null);
    }

    /**
     * 查询用户的部门及其所有子部门 ID
     * <p>
     * 替代原 SysUserMapper.findUserDeptAndChildren
     *
     * @param userId 用户 ID
     * @return 部门及子部门 ID 列表
     */
    public List<UUID> findUserDeptAndChildren(UUID userId) {
        // 1. 从 user 库获取用户的部门 ID
        UUID deptId = userMapper.getUserDeptId(userId);
        if (deptId == null) {
            return Collections.emptyList();
        }

        // 2. 从 org 库递归查询部门及子部门
        return deptMapper.selectDeptAndChildren(deptId);
    }

    /**
     * 检查用户是否有权访问指定部门
     * <p>
     * 替代原 SysUserMapper.hasAccessToDept
     *
     * @param userId 用户 ID
     * @param deptId 目标部门 ID
     * @return 是否有访问权限
     */
    public boolean hasAccessToDept(UUID userId, UUID deptId) {
        // 1. 获取用户的数据权限范围
        Integer dataScope = userRoleMapper.getUserDataScope(userId);
        if (dataScope == null) {
            return false;
        }

        // 数据权限：1-全部数据
        if (dataScope == 1) {
            return true;
        }

        // 2. 获取用户的部门 ID
        UUID userDeptId = userMapper.getUserDeptId(userId);
        if (userDeptId == null) {
            return false;
        }

        // 数据权限：3-本部门
        if (dataScope == 3) {
            return userDeptId.equals(deptId);
        }

        // 数据权限：4-本部门及子部门
        if (dataScope == 4) {
            List<UUID> accessibleDepts = deptMapper.selectDeptAndChildren(userDeptId);
            return accessibleDepts.contains(deptId);
        }

        // 数据权限：5-仅本人（不能访问其他部门）
        return false;
    }

    // ==================== 部门相关跨库查询 ====================

    /**
     * 查询部门树（包含负责人信息）
     * <p>
     * 替代原 SysDeptMapper.selectDeptTree
     *
     * @return 部门 DTO 列表（包含负责人姓名）
     */
    public List<DeptDTO> selectDeptTree() {
        // 1. 从 org 库查询所有部门
        List<SysDept> depts = deptMapper.selectDeptList();
        if (depts == null || depts.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 收集所有负责人 ID
        Set<UUID> leaderIds = depts.stream()
                .map(SysDept::getLeaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 从 user 库批量查询负责人信息
        Map<UUID, String> leaderNameMap = new HashMap<>();
        if (!leaderIds.isEmpty()) {
            List<SysUser> leaders = userMapper.selectBasicInfoByIds(new ArrayList<>(leaderIds));
            leaderNameMap = leaders.stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        }

        // 4. 组装 DTO
        Map<UUID, String> finalLeaderNameMap = leaderNameMap;
        return depts.stream()
                .map(dept -> {
                    DeptDTO dto = new DeptDTO();
                    dto.setId(dept.getId());
                    dto.setParentId(dept.getParentId());
                    dto.setDeptCode(dept.getDeptCode());
                    dto.setDeptName(dept.getDeptName());
                    dto.setDeptType(dept.getDeptType());
                    dto.setLeaderId(dept.getLeaderId());
                    dto.setLeaderName(dept.getLeaderId() != null ?
                            finalLeaderNameMap.get(dept.getLeaderId()) : null);
                    dto.setPhone(dept.getPhone());
                    dto.setEmail(dept.getEmail());
                    dto.setIsolationLevel(dept.getIsolationLevel());
                    dto.setSortOrder(dept.getSortOrder());
                    dto.setStatus(dept.getStatus());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // ==================== 角色相关跨库查询 ====================

    /**
     * 获取角色可访问的部门 ID 列表（递归包含子部门）
     * <p>
     * 替代原 SysRoleDeptMapper.findAccessibleDeptIds
     *
     * @param roleId 角色 ID
     * @return 可访问的部门 ID 列表
     */
    public List<UUID> findAccessibleDeptIds(UUID roleId) {
        Set<UUID> result = new HashSet<>();

        // 1. 获取不需要递归的部门 ID
        List<UUID> directDeptIds = roleDeptMapper.findDeptIdsWithoutChildren(roleId);
        if (directDeptIds != null) {
            result.addAll(directDeptIds);
        }

        // 2. 获取需要递归子部门的部门 ID
        List<UUID> deptIdsWithChildren = roleDeptMapper.findDeptIdsWithChildren(roleId);
        if (deptIdsWithChildren != null && !deptIdsWithChildren.isEmpty()) {
            // 3. 从 org 库递归查询子部门
            List<UUID> allChildDepts = deptMapper.selectDeptsAndChildren(deptIdsWithChildren);
            if (allChildDepts != null) {
                result.addAll(allChildDepts);
            }
        }

        return new ArrayList<>(result);
    }

    // ==================== 即将过期角色提醒 ====================

    /**
     * 查询即将过期的角色（包含用户信息）
     * <p>
     * 替代原 SysUserMapper.findExpiringRoles
     *
     * @param days 天数
     * @return 即将过期的角色信息列表
     */
    public List<Map<String, Object>> findExpiringRolesWithUserInfo(Integer days) {
        List<Map<String, Object>> expiringRoles = userRoleMapper.findExpiringRolesForNotification(days);
        return enrichRolesWithUserInfo(expiringRoles, true);
    }

    /**
     * 查询已过期的角色（包含用户信息）
     * <p>
     * 替代原 SysUserMapper.findExpiredRoles
     *
     * @return 已过期的角色信息列表
     */
    public List<Map<String, Object>> findExpiredRolesWithUserInfo() {
        List<Map<String, Object>> expiredRoles = userRoleMapper.findExpiredRolesForCleanup();
        return enrichRolesWithUserInfo(expiredRoles, false);
    }

    /**
     * 为角色列表补充用户信息
     *
     * @param roles        角色列表
     * @param includeEmail 是否包含邮箱字段
     * @return 包含用户信息的角色列表
     */
    private List<Map<String, Object>> enrichRolesWithUserInfo(
            List<Map<String, Object>> roles, boolean includeEmail) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 收集用户 ID
        Set<UUID> userIds = roles.stream()
                .map(m -> (UUID) m.get("user_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 从 user 库批量查询用户信息
        Map<UUID, SysUser> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            List<SysUser> users = userMapper.selectBasicInfoByIds(new ArrayList<>(userIds));
            userMap = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        }

        // 3. 组装结果
        Map<UUID, SysUser> finalUserMap = userMap;
        return roles.stream()
                .map(m -> {
                    Map<String, Object> result = new HashMap<>(m);
                    UUID userId = (UUID) m.get("user_id");
                    SysUser user = finalUserMap.get(userId);
                    if (user != null) {
                        result.put("username", user.getUsername());
                        if (includeEmail) {
                            result.put("email", user.getEmail());
                        }
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }
}
