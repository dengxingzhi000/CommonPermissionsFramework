package com.frog.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frog.common.response.ResultCode;
import com.frog.common.util.UUIDv7Util;

import com.frog.common.exception.BusinessException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.frog.common.dto.permission.PermissionDTO;
import com.frog.common.dto.user.UserDTO;
import com.frog.common.dto.user.UserInfo;
import com.frog.common.web.util.SecurityUtils;
import com.frog.system.domain.entity.SysUser;
import com.frog.system.event.DataSyncEventPublisher;
import com.frog.system.mapper.SysPermissionMapper;
import com.frog.system.mapper.SysUserMapper;
import com.frog.system.mapper.SysUserRoleMapper;
import com.frog.system.service.CrossDatabaseQueryService;
import com.frog.system.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author author
 * @since 2025-10-14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final CrossDatabaseQueryService crossDbService;
    private final PasswordEncoder passwordEncoder;
    private final DataSyncEventPublisher dataSyncEventPublisher;

    @Value("${spring.security.default-password}")
    private String defaultPassword;

    /**
     * 分页查询用户列表
     */
    public Page<UserDTO> listUsers(Integer pageNum, Integer pageSize,
                                   String username, Integer status) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(username != null && !username.isEmpty(), SysUser::getUsername, username)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);

        Page<SysUser> userPage = userMapper.selectPage(page, wrapper);

        // 转换为 DTO
        Page<UserDTO> userDTOPage = new Page<>(pageNum, pageSize, userPage.getTotal());
        List<UserDTO> userDTOs = userPage.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        userDTOPage.setRecords(userDTOs);

        return userDTOPage;
    }

    /**
     * 根据 ID查询用户
     */
    @Cacheable(
            value = "user",
            key = "#id"
    )
    public UserDTO getUserById(UUID id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        UserDTO userDTO = convertToDTO(user);

        // PERFORMANCE: Single query to fetch both role IDs and names (fixes N+1 issue)
        // 跨库查询 db_permission
        List<Map<String, Object>> roles = userRoleMapper.findUserRolesWithNames(id);

        if (!roles.isEmpty()) {
            List<UUID> roleIds = roles.stream()
                    .map(role -> (UUID) role.get("id"))
                    .toList();
            List<String> roleNames = roles.stream()
                    .map(role -> (String) role.get("name"))
                    .toList();

            userDTO.setRoleIds(roleIds);
            userDTO.setRoleNames(roleNames);
        }

        return userDTO;
    }

    /**
     * 获取用户详细信息（包含权限和菜单）
     */
    @Cacheable(
            value = "userInfo",
            key = "#userId"
    )
    public UserInfo getUserInfo(UUID userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        UserInfo userInfo = UserInfo.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .email(user.getEmail())
                .phone(user.getPhone())
                .deptId(user.getDeptId())
                .userLevel(user.getUserLevel())
                .build();

        // 查询角色和权限（跨库查询 db_permission）
        Set<String> roles = userRoleMapper.findRoleCodesByUserId(userId);
        Set<String> permissions = userRoleMapper.findPermissionCodesByUserId(userId);

        userInfo.setRoles(roles);
        userInfo.setPermissions(permissions);

        // 构建菜单树（只返回菜单类型的权限）
        List<PermissionDTO> menuTree = permissionMapper.findMenuTreeByUserId(userId);
        userInfo.setMenuTree(new HashSet<>(menuTree));

        return userInfo;
    }

    /**
     * 新增用户
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo"},
            allEntries = true
    )
    public void addUser(UserDTO userDTO) {
        // 1. Validation
        if (userMapper.existsByUsername(userDTO.getUsername())) {
            throw new BusinessException(ResultCode.USER_EXIST.getCode(), ResultCode.USER_EXIST.getMessage());
        }

        // 2. Password encoding
        String encodedPassword;
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            encodedPassword = passwordEncoder.encode(userDTO.getPassword());
        } else {
            encodedPassword = passwordEncoder.encode(defaultPassword);
        }

        // 3. Prepare entity
        SysUser user = new SysUser();
        BeanUtils.copyProperties(userDTO, user);
        user.setPassword(encodedPassword);
        user.setId(UUIDv7Util.generate());
        user.setPasswordExpireTime(LocalDateTime.now().plusDays(90));
        user.setForceChangePassword(true);

        // 4. Database operations
        userMapper.insert(user);

        // 跨库操作：插入用户角色关联（db_permission）
        if (userDTO.getRoleIds() != null && !userDTO.getRoleIds().isEmpty()) {
            userRoleMapper.batchInsert(user.getId(), userDTO.getRoleIds(),
                    SecurityUtils.getCurrentUserUuid().orElse(null));
        }

        // 5. Publish sync event for redundancy update
        dataSyncEventPublisher.publishUserCreated(user);

        log.info("User created: {}, by: {}", user.getUsername(), SecurityUtils.getCurrentUsername());
    }

    /**
     * 修改用户
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo"},
            key = "#userDTO.id"
    )
    public void updateUser(UserDTO userDTO) {
        SysUser existUser = userMapper.selectById(userDTO.getId());
        if (existUser == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        SysUser user = new SysUser();
        BeanUtils.copyProperties(userDTO, user);

        user.setPassword(null);

        userMapper.updateById(user);

        // 跨库操作：更新用户角色关联（db_permission）
        if (userDTO.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(user.getId());
            if (!userDTO.getRoleIds().isEmpty()) {
                userRoleMapper.batchInsert(user.getId(), userDTO.getRoleIds(),
                        SecurityUtils.getCurrentUserUuid().orElse(null));
            }
        }

        // Publish sync event for redundancy update
        SysUser updatedUser = userMapper.selectById(user.getId());
        dataSyncEventPublisher.publishUserUpdated(updatedUser);

        log.info("User updated: {}, by: {}", user.getUsername(), SecurityUtils.getCurrentUsername());
    }

    /**
     * 删除用户（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo"},
            key = "#id"
    )
    public void deleteUser(UUID id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        if (user.getId().equals(UUID.fromString("019a0aee-3b74-7bfc-b34f-48b5428d4875"))) {
            throw new BusinessException(ResultCode.USER_CANNOT_DELETE_ADMIN.getCode(),
                    ResultCode.USER_CANNOT_DELETE_ADMIN.getMessage());
        }

        if (user.getId().equals(SecurityUtils.getCurrentUserUuid().orElse(null))) {
            throw new BusinessException(ResultCode.USER_CANNOT_DELETE_SELF.getCode(),
                    ResultCode.USER_CANNOT_DELETE_SELF.getMessage());
        }

        userMapper.deleteById(id);

        // Publish sync event for redundancy update
        dataSyncEventPublisher.publishUserDeleted(id);

        log.info("User deleted: {}, by: {}", user.getUsername(), SecurityUtils.getCurrentUsername());
    }

    /**
     * 重置密码
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails"},
            key = "#id"
    )
    public String resetPassword(UUID id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String newPassword = generateRandomPassword();

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(true);

        userMapper.updateById(user);

        log.info("Password reset for user: {}, by: {}",
                user.getUsername(), SecurityUtils.getCurrentUsername());

        return newPassword;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        password.append(chars.charAt(random.nextInt(26)));
        password.append(chars.charAt(26 + random.nextInt(26)));
        password.append(chars.charAt(52 + random.nextInt(10)));
        password.append(chars.charAt(62 + random.nextInt(4)));

        for (int i = 0; i < 8; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return shufflePassword(password.toString(), random);
    }

    private String shufflePassword(String password, SecureRandom random) {
        List<Character> charList = password.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        Collections.shuffle(charList, random);

        return charList.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails"},
            key = "#userId"
    )
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForceChangePassword(false);
        user.setLastPasswordChangeTime(LocalDateTime.now());

        LocalDateTime passwordExpireTime = LocalDateTime.now().plusDays(90);
        user.setPasswordExpireTime(passwordExpireTime);

        userMapper.updateById(user);

        log.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * 授权角色
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo", "userRoles", "userPermissions"},
            key = "#userId"
    )
    public void grantRoles(UUID userId, List<UUID> roleIds) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        // 跨库操作：更新用户角色关联（db_permission）
        userRoleMapper.deleteByUserId(userId);

        if (roleIds != null && !roleIds.isEmpty()) {
            userRoleMapper.batchInsert(userId, roleIds, SecurityUtils.getCurrentUserUuid().orElse(null));
        }

        log.info("Roles granted to user: {}, roles: {}, by: {}",
                user.getUsername(), roleIds, SecurityUtils.getCurrentUsername());
    }

    /**
     * 锁定/解锁用户
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails"},
            key = "#id"
    )
    public void lockUser(UUID id, Boolean lock) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        if (lock) {
            user.setStatus(2);
            LocalDateTime lockedUntil = LocalDateTime.now().plusHours(24);
            user.setLockedUntil(lockedUntil);
        } else {
            user.setStatus(1);
            user.setLockedUntil(null);
            user.setLoginAttempts(0);
        }

        userMapper.updateById(user);

        log.info("User {} {}, by: {}",
                user.getUsername(), lock ? "locked" : "unlocked",
                SecurityUtils.getCurrentUsername());
    }

    /**
     * 授予临时角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo", "userRoles", "userPermissions"},
            key = "#userId"
    )
    public void grantTemporaryRoles(UUID userId, List<UUID> roleIds,
                                    LocalDateTime effectiveTime, LocalDateTime expireTime) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND.getCode(), ResultCode.USER_NOT_FOUND.getMessage());
        }

        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("过期时间不能早于当前时间");
        }

        if (effectiveTime != null && expireTime != null && effectiveTime.isAfter(expireTime)) {
            throw new BusinessException("生效时间不能晚于过期时间");
        }

        // 跨库操作：插入临时用户角色关联（db_permission）
        if (roleIds != null && !roleIds.isEmpty()) {
            userRoleMapper.batchInsertTemporary(
                    userId, roleIds,
                    effectiveTime != null ? effectiveTime : LocalDateTime.now(),
                    expireTime,
                    SecurityUtils.getCurrentUserUuid().orElse(null)
            );
        }

        log.info("Temporary roles granted to user: {}, roles: {}, expireTime: {}, by: {}",
                user.getUsername(), roleIds, expireTime, SecurityUtils.getCurrentUsername());
    }

    /**
     * 延长临时角色的有效期
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo", "userRoles"},
            key = "#userId"
    )
    public void extendTemporaryRole(UUID userId, UUID roleId, LocalDateTime newExpireTime) {
        // 跨库查询 db_permission
        if (!userRoleMapper.hasTemporaryRole(userId, roleId)) {
            throw new BusinessException("用户不存在该临时角色或已过期");
        }

        if (newExpireTime.isBefore(LocalDateTime.now())) {
            throw new BusinessException("新的过期时间不能早于当前时间");
        }

        int updated = userRoleMapper.extendTemporaryRole(userId, roleId, newExpireTime);
        if (updated == 0) {
            throw new BusinessException("延长临时角色失败");
        }

        log.info("Temporary role extended: userId={}, roleId={}, newExpireTime={}, by={}",
                userId, roleId, newExpireTime, SecurityUtils.getCurrentUsername());
    }

    /**
     * 提前终止临时角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(
            value = {"user", "userDetails", "userInfo", "userRoles", "userPermissions"},
            key = "#userId"
    )
    public void terminateTemporaryRole(UUID userId, UUID roleId) {
        // 跨库操作 db_permission
        int updated = userRoleMapper.terminateTemporaryRole(userId, roleId);
        if (updated == 0) {
            throw new BusinessException("终止临时角色失败，可能该角色不存在或已过期");
        }

        log.info("Temporary role terminated: userId={}, roleId={}, by={}",
                userId, roleId, SecurityUtils.getCurrentUsername());
    }

    /**
     * 查询用户的临时角色列表
     */
    @Override
    @Cacheable(
            value = "userTemporaryRoles",
            key = "#userId"
    )
    public List<Map<String, Object>> getUserTemporaryRoles(UUID userId) {
        // 跨库查询 db_permission
        return userRoleMapper.findTemporaryRolesByUserId(userId);
    }

    /**
     * 检查用户是否有访问某个部门的权限
     * <p>
     * 跨库查询：需要查询 db_permission 和 db_org
     */
    @Override
    public boolean canAccessDept(UUID userId, UUID deptId) {
        return crossDbService.hasAccessToDept(userId, deptId);
    }

    /**
     * 获取用户的数据权限范围
     */
    @Override
    @Cacheable(
            value = "userDataScope",
            key = "#userId"
    )
    public Integer getUserDataScope(UUID userId) {
        // 跨库查询 db_permission
        Integer dataScope = userRoleMapper.getUserDataScope(userId);
        return dataScope != null ? dataScope : 5;
    }

    /**
     * 统计用户信息
     * <p>
     * 跨库查询 db_permission
     */
    @Override
    public Map<String, Object> getUserStatistics(UUID userId) {
        Map<String, Object> stats = new HashMap<>();

        Integer roleCount = userRoleMapper.countUserRoles(userId);
        stats.put("roleCount", roleCount);

        Integer tempRoleCount = userRoleMapper.countTemporaryRoles(userId);
        stats.put("temporaryRoleCount", tempRoleCount);

        Integer expiringCount = userRoleMapper.countExpiringRoles(userId, 7);
        stats.put("expiringRoleCount", expiringCount);

        Integer dataScope = userRoleMapper.getUserDataScope(userId);
        stats.put("dataScope", dataScope != null ? dataScope : 5);

        BigDecimal maxApprovalAmount = userRoleMapper.getMaxApprovalAmount(userId);
        stats.put("maxApprovalAmount", maxApprovalAmount);

        return stats;
    }

    @Override
    public void updateLastLogin(UUID userId, String ipAddress) {
       LambdaUpdateWrapper<SysUser> updateWrapper = new LambdaUpdateWrapper<>();
       updateWrapper.eq(SysUser::getId, userId)
                   .set(SysUser::getLastLoginTime, LocalDateTime.now())
                   .set(SysUser::getLastLoginIp, ipAddress);
       userMapper.update(null, updateWrapper);
    }

    private UserDTO convertToDTO(SysUser user) {
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);

        return userDTO;
    }
}
