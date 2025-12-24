package com.frog.system.rpc;

import com.frog.common.dto.user.UserDTO;
import com.frog.common.dto.user.UserInfo;
import com.frog.common.web.domain.SecurityUser;
import com.frog.system.service.ISysPermissionService;
import com.frog.system.service.ISysUserService;
import com.frog.system.api.UserDubboService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * User Dubbo Service Implementation
 * Provides high-performance RPC for user-related operations
 *
 * @author Deng
 * @version 2.0
 */
@DubboService
@Component
public class UserDubboServiceImpl implements UserDubboService {
    private final ISysUserService sysUserService;
    private final ISysPermissionService sysPermissionService;

    public UserDubboServiceImpl(ISysUserService sysUserService,
                                ISysPermissionService sysPermissionService) {
        this.sysUserService = sysUserService;
        this.sysPermissionService = sysPermissionService;
    }

    @Override
    public SecurityUser getUserByUsername(String username) {
        return sysUserService.getUserByUsername(username);
    }

    @Override
    public Set<String> getUserRoles(UUID userId) {
        return sysPermissionService.getUserRoles(userId);
    }

    @Override
    public Set<String> getUserPermissions(UUID userId) {
        return sysPermissionService.getUserPermissions(userId);
    }

    @Override
    public UserInfo getUserInfo(UUID userId) {
        return sysUserService.getUserInfo(userId);
    }

    @Override
    public void updateLastLogin(UUID userId, String ipAddress, LocalDateTime loginTime) {
        // Delegate to existing method; ignore loginTime if not used.
        sysUserService.updateLastLogin(userId, ipAddress);
    }

    @Override
    public UserDTO getUserById(UUID userId) {
        return sysUserService.getUserById(userId);
    }

    @Override
    public Set<String> findRolesByUserId(UUID userId) {
        return sysPermissionService.getUserRoles(userId);
    }

    @Override
    public Set<String> findPermissionsByUserId(UUID userId) {
        return sysPermissionService.getUserPermissions(userId);
    }
}

