package com.frog.system.service.Impl;

import com.frog.common.web.domain.SecurityUser;
import com.frog.system.mapper.SysUserMapper;
import com.frog.system.mapper.SysUserRoleMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * UserDetailsService 实现
 *
 * @author Deng
 * createData 2025/10/14 14:54
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {
    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    @Cacheable(
            value = "userDetails",
            key = "#username",
            unless = "#result == null"
    )
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        // 1. 从 db_user 库查询用户基本信息
        var user = sysUserMapper.findByUsername(username);
        if (user == null || user.getDeleted()) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("用户不存在或已删除: " + username);
        }

        // 2. 从 db_permission 库查询用户角色（跨库查询）
        Set<String> roles = sysUserRoleMapper.findRoleCodesByUserId(user.getId());

        // 3. 从 db_permission 库查询用户权限（跨库查询）
        Set<String> permissions = sysUserRoleMapper.findPermissionCodesByUserId(user.getId());

        SecurityUser securityUser = SecurityUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .realName(user.getRealName())
                .deptId(user.getDeptId())
                .status(user.getStatus())
                .accountType(user.getAccountType())
                .userLevel(user.getUserLevel())
                .roles(roles)
                .permissions(permissions)
                .twoFactorEnabled(user.getTwoFactorEnabled())
                .passwordExpireTime(user.getPasswordExpireTime())
                .forceChangePassword(user.getForceChangePassword())
                .build();

        log.info("User loaded: {}, Roles: {}, Permissions count: {}",
                username, roles, permissions.size());

        return securityUser;
    }
}
