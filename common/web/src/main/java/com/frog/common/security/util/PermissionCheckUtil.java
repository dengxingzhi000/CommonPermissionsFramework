package com.frog.common.security.util;

import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
/**
 * 权限验证工具类
 * 提供编程式权限验证方法
 *
 * @author Deng
 * createData 2025/10/30 13:43
 * @version 1.0
 */
@Component
public class PermissionCheckUtil {

    /**
     * 验证当前用户是否拥有指定权限
     */
    public boolean hasPermission(String permission) {
        return executeWithUser(user -> {
            Set<String> permissions = user.getPermissions();
            return permissions != null && permissions.contains(permission);
        });
    }

    /**
     * 验证当前用户是否拥有任一权限
     */
    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        
        return executeWithUser(user -> {
            Set<String> userPermissions = user.getPermissions();
            if (userPermissions == null || userPermissions.isEmpty()) {
                return false;
            }

            for (String permission : permissions) {
                if (userPermissions.contains(permission)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 验证当前用户是否拥有所有权限
     */
    public boolean hasAllPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        
        return executeWithUser(user -> {
            Set<String> userPermissions = user.getPermissions();
            if (userPermissions == null || userPermissions.isEmpty()) {
                return false;
            }

            for (String permission : permissions) {
                if (!userPermissions.contains(permission)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * 验证当前用户是否拥有指定角色
     */
    public boolean hasRole(String role) {
        return executeWithUser(user -> {
            Set<String> roles = user.getRoles();
            return roles != null && roles.contains(role);
        });
    }

    /**
     * 验证当前用户是否拥有任一角色
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        
        return executeWithUser(user -> {
            Set<String> userRoles = user.getRoles();
            if (userRoles == null || userRoles.isEmpty()) {
                return false;
            }

            for (String role : roles) {
                if (userRoles.contains(role)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * 验证当前用户是否拥有所有角色
     */
    public boolean hasAllRoles(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        
        return executeWithUser(user -> {
            Set<String> userRoles = user.getRoles();
            if (userRoles == null || userRoles.isEmpty()) {
                return false;
            }

            for (String role : roles) {
                if (!userRoles.contains(role)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * 验证用户是否为超级管理员
     */
    public boolean isSuperAdmin() {
        return hasRole("ROLE_SUPER_ADMIN");
    }

    /**
     * 验证用户是否有权访问指定部门的数据
     */
    public boolean canAccessDept(UUID deptId) {
        return executeWithUser(user -> {
            // 超级管理员可以访问所有部门
            if (isSuperAdmin()) {
                return true;
            }

            // 用户自己的部门
            return user.getDeptId() != null && user.getDeptId().equals(deptId);

            // TODO: 根据数据权限范围判断
            // 需要查询用户的数据权限配置
        });
    }

    /**
     * 验证用户是否有权访问指定用户的数据
     */
    public boolean canAccessUser(UUID targetUserId) {
        return executeWithUser(user -> {
            // 超级管理员可以访问所有用户
            if (isSuperAdmin()) {
                return true;
            }

            // 用户可以访问自己的数据
            return user.getUserId().equals(targetUserId);

            // TODO: 根据数据权限范围判断
        });
    }

    /**
     * 获取当前用户的所有权限
     */
    public Set<String> getCurrentUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null && !auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());
    }

    /**
     * 获取当前用户的所有角色
     */
    public Set<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth != null && auth.startsWith("ROLE_"))
                .map(auth -> auth.substring(5)) // 移除"ROLE_"前缀
                .collect(Collectors.toSet());
    }
    
    /**
     * 使用当前用户执行操作的通用方法(返回boolean类型)
     * @param function 要执行的操作
     * @return 操作结果或默认值 false
     */
    private boolean executeWithUser(Function<SecurityUser, Boolean> function) {
        SecurityUser user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return false;
        }
        return function.apply(user);
    }
}
