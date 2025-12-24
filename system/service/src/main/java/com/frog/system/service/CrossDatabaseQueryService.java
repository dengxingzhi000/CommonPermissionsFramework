package com.frog.system.service;

import com.frog.common.data.rw.annotation.Master;
import com.frog.common.data.rw.annotation.Slave;
import com.frog.common.dto.dept.DeptDTO;
import com.frog.common.dto.permission.PermissionDTO;
import com.frog.system.domain.entity.SysDept;
import com.frog.system.domain.entity.SysUser;
import com.frog.system.mapper.*;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final SysPermissionMapper permissionMapper;

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

    /**
     * 查询用户角色（带角色名称）
     * <p>
     * 替代 SysUserRoleMapper.findUserRolesWithNames
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 角色列表（包含 id, name 字段）
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "findUserRolesWithNames"})
    @Cacheable(value = "userRoles", key = "#userId", unless = "#result.isEmpty()")
    public List<Map<String, Object>> findUserRolesWithNames(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userRoleMapper.findUserRolesWithNames(userId);
    }

    /**
     * 查询用户角色编码集合
     * <p>
     * 替代 SysUserRoleMapper.findRoleCodesByUserId
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 角色编码集合
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "findRoleCodesByUserId"})
    @Cacheable(value = "userRoleCodes", key = "#userId", unless = "#result.isEmpty()")
    public Set<String> findRoleCodesByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return userRoleMapper.findRoleCodesByUserId(userId);
    }

    /**
     * 查询用户权限编码集合
     * <p>
     * 替代 SysUserRoleMapper.findPermissionCodesByUserId
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 权限编码集合
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "findPermissionCodesByUserId"})
    @Cacheable(value = "userPermissionCodes", key = "#userId", unless = "#result.isEmpty()")
    public Set<String> findPermissionCodesByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return userRoleMapper.findPermissionCodesByUserId(userId);
    }

    /**
     * 查询用户菜单树
     * <p>
     * 替代 SysPermissionMapper.findMenuTreeByUserId
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 菜单权限 DTO 列表
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "findMenuTreeByUserId"})
    @Cacheable(value = "userMenuTree", key = "#userId", unless = "#result.isEmpty()")
    public List<PermissionDTO> findMenuTreeByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return permissionMapper.findMenuTreeByUserId(userId);
    }

    /**
     * 获取用户数据权限范围
     * <p>
     * 替代 SysUserRoleMapper.getUserDataScope
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 数据权限范围（1-全部, 2-自定义, 3-本部门, 4-本部门及子部门, 5-仅本人）
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "getUserDataScope"})
    @Cacheable(value = "userDataScope", key = "#userId")
    public Integer getUserDataScope(UUID userId) {
        if (userId == null) {
            return 5; // 默认仅本人
        }
        Integer dataScope = userRoleMapper.getUserDataScope(userId);
        return dataScope != null ? dataScope : 5;
    }

    /**
     * 批量插入用户角色关联（永久授权）
     * <p>
     * 替代 SysUserRoleMapper.batchInsert
     * 跨库操作：db_permission
     *
     * @param userId   用户 ID
     * @param roleIds  角色 ID 列表
     * @param createBy 创建人 ID
     * @return 插入行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertUserRoles(UUID userId, List<UUID> roleIds, UUID createBy) {
        if (userId == null || roleIds == null || roleIds.isEmpty()) {
            return 0;
        }
        log.debug("Batch inserting user roles: userId={}, roleCount={}", userId, roleIds.size());
        return userRoleMapper.batchInsert(userId, roleIds, createBy);
    }

    /**
     * 批量插入临时用户角色关联
     * <p>
     * 替代 SysUserRoleMapper.batchInsertTemporary
     * 跨库操作：db_permission
     *
     * @param userId        用户 ID
     * @param roleIds       角色 ID 列表
     * @param effectiveTime 生效时间
     * @param expireTime    过期时间
     * @param createBy      创建人 ID
     * @return 插入行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertTemporaryUserRoles(UUID userId, List<UUID> roleIds,
                                              LocalDateTime effectiveTime,
                                              LocalDateTime expireTime,
                                              UUID createBy) {
        if (userId == null || roleIds == null || roleIds.isEmpty()) {
            return 0;
        }
        log.debug("Batch inserting temporary user roles: userId={}, roleCount={}, expireTime={}",
                userId, roleIds.size(), expireTime);
        return userRoleMapper.batchInsertTemporary(userId, roleIds, effectiveTime, expireTime, createBy);
    }

    /**
     * 删除用户的所有角色关联
     * <p>
     * 替代 SysUserRoleMapper.deleteByUserId
     * 跨库操作：db_permission
     *
     * @param userId 用户 ID
     * @return 删除行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int deleteUserRoles(UUID userId) {
        if (userId == null) {
            return 0;
        }
        log.debug("Deleting user roles: userId={}", userId);
        return userRoleMapper.deleteByUserId(userId);
    }

    /**
     * 检查用户是否拥有指定的临时角色
     * <p>
     * 替代 SysUserRoleMapper.hasTemporaryRole
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 是否拥有该临时角色
     */
    @Slave
    public boolean hasTemporaryRole(UUID userId, UUID roleId) {
        if (userId == null || roleId == null) {
            return false;
        }
        return userRoleMapper.hasTemporaryRole(userId, roleId);
    }

    /**
     * 延长临时角色有效期
     * <p>
     * 替代 SysUserRoleMapper.extendTemporaryRole
     * 跨库操作：db_permission
     *
     * @param userId        用户 ID
     * @param roleId        角色 ID
     * @param newExpireTime 新的过期时间
     * @return 更新行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int extendTemporaryRole(UUID userId, UUID roleId, LocalDateTime newExpireTime) {
        if (userId == null || roleId == null || newExpireTime == null) {
            return 0;
        }
        return userRoleMapper.extendTemporaryRole(userId, roleId, newExpireTime);
    }

    /**
     * 终止临时角色
     * <p>
     * 替代 SysUserRoleMapper.terminateTemporaryRole
     * 跨库操作：db_permission
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 更新行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int terminateTemporaryRole(UUID userId, UUID roleId) {
        if (userId == null || roleId == null) {
            return 0;
        }
        return userRoleMapper.terminateTemporaryRole(userId, roleId);
    }

    /**
     * 查询用户的临时角色列表
     * <p>
     * 替代 SysUserRoleMapper.findTemporaryRolesByUserId
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 临时角色列表
     */
    @Slave
    @Cacheable(value = "userTemporaryRoles", key = "#userId", unless = "#result.isEmpty()")
    public List<Map<String, Object>> findTemporaryRolesByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userRoleMapper.findTemporaryRolesByUserId(userId);
    }

    /**
     * 统计用户有效角色数
     * <p>
     * 替代 SysUserRoleMapper.countUserRoles
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 有效角色数
     */
    @Slave
    public Integer countUserRoles(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Integer count = userRoleMapper.countUserRoles(userId);
        return count != null ? count : 0;
    }

    /**
     * 统计用户临时角色数
     * <p>
     * 替代 SysUserRoleMapper.countTemporaryRoles
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 临时角色数
     */
    @Slave
    public Integer countTemporaryRoles(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Integer count = userRoleMapper.countTemporaryRoles(userId);
        return count != null ? count : 0;
    }

    /**
     * 统计用户即将过期的角色数
     * <p>
     * 替代 SysUserRoleMapper.countExpiringRoles
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @param days   天数
     * @return 即将过期的角色数
     */
    @Slave
    public Integer countExpiringRoles(UUID userId, Integer days) {
        if (userId == null) {
            return 0;
        }
        Integer count = userRoleMapper.countExpiringRoles(userId, days);
        return count != null ? count : 0;
    }

    /**
     * 获取用户最大审批金额
     * <p>
     * 替代 SysUserRoleMapper.getMaxApprovalAmount
     * 跨库查询：db_permission
     *
     * @param userId 用户 ID
     * @return 最大审批金额
     */
    @Slave
    public java.math.BigDecimal getMaxApprovalAmount(UUID userId) {
        if (userId == null) {
            return java.math.BigDecimal.ZERO;
        }
        return userRoleMapper.getMaxApprovalAmount(userId);
    }

    /**
     * 批量统计部门用户数
     * <p>
     * 替代 SysUserMapper.countUsersByDeptIds
     * 跨库查询：db_user
     *
     * @param deptIds 部门 ID 列表
     * @return 部门 ID → 用户数 映射
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "countUsersByDeptIds"})
    public Map<UUID, Integer> countUsersByDeptIds(List<UUID> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, Map<String, Object>> countResult = userMapper.countUsersByDeptIds(deptIds);
        if (countResult == null) {
            return Collections.emptyMap();
        }

        Map<UUID, Integer> result = new HashMap<>();
        countResult.forEach((deptId, row) -> {
            Object count = row.get("user_count");
            result.put(deptId, count != null ? ((Number) count).intValue() : 0);
        });
        return result;
    }

    /**
     * 统计单个部门用户数
     * <p>
     * 替代 SysUserMapper.countUsersByDeptId
     * 跨库查询：db_user
     *
     * @param deptId 部门 ID
     * @return 用户数
     */
    @Slave
    public int countUsersByDeptId(UUID deptId) {
        if (deptId == null) {
            return 0;
        }
        return userMapper.countUsersByDeptId(deptId);
    }

    /**
     * 获取用户基本信息
     * <p>
     * 替代 SysUserMapper.selectById
     * 跨库查询：db_user
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "getUserBasicInfo"})
    public SysUser getUserBasicInfo(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    /**
     * 批量获取用户基本信息
     * <p>
     * 替代 SysUserMapper.selectBasicInfoByIds
     * 跨库查询：db_user
     *
     * @param userIds 用户 ID 列表
     * @return 用户实体列表
     */
    @Slave
    @Timed(value = "cross_db_query", extraTags = {"method", "getUserBasicInfoBatch"})
    public List<SysUser> getUserBasicInfoBatch(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBasicInfoByIds(userIds);
    }

    /**
     * 批量获取用户基本信息（Map 形式）
     *
     * @param userIds 用户 ID 列表
     * @return 用户 ID → 用户实体 映射
     */
    @Slave
    public Map<UUID, SysUser> getUserBasicInfoMap(List<UUID> userIds) {
        List<SysUser> users = getUserBasicInfoBatch(userIds);
        return users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
    }

    /**
     * 获取部门负责人 ID
     * <p>
     * 替代 SysDeptMapper.getLeaderId
     * 跨库查询：db_org
     *
     * @param deptId 部门 ID
     * @return 负责人 ID
     */
    @Slave
    public UUID getDeptLeaderId(UUID deptId) {
        if (deptId == null) {
            return null;
        }
        return deptMapper.getLeaderId(deptId);
    }

    /**
     * 删除部门的角色关联
     * <p>
     * 用于删除部门时清理 db_permission.sys_role_dept 中的关联数据
     * 跨库操作：db_org → db_permission
     *
     * @param deptId 部门 ID
     * @return 删除行数
     */
    @Master(reason = "写操作必须走主库")
    @Transactional(rollbackFor = Exception.class)
    public int deleteRoleDeptsByDeptId(UUID deptId) {
        if (deptId == null) {
            return 0;
        }
        log.debug("Deleting role-dept associations for deptId={}", deptId);
        return roleDeptMapper.deleteByDeptId(deptId);
    }
}
