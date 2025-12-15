package com.frog.common.feign.client;

import com.frog.common.dto.user.UserInfo;
import com.frog.common.response.ApiResponse;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.feign.fallback.UserServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * 用户服务Feign客户端
 * 用于服务间调用
 *
 * @author Deng
 * createData 2025/10/31 9:50
 * @version 1.0
 */
@FeignClient(
        name = "user-service",
        path = "/api/system/users",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface SysUserServiceClient {
    /**
     * 根据用户名查询用户（用于认证）
     */
    @GetMapping("/by-username/{username}")
    ApiResponse<SecurityUser> getUserByUsername(@PathVariable String username);

    /**
     * 查询用户角色
     */
    @GetMapping("/{userId}/roles")
    ApiResponse<Set<String>> getUserRoles(@PathVariable UUID userId);

    /**
     * 查询用户权限
     */
    @GetMapping("/{userId}/permissions")
    ApiResponse<Set<String>> getUserPermissions(@PathVariable UUID userId);

    /**
     * 更新最后登录信息
     */
    @GetMapping("/{userId}/update-login")
    ApiResponse<Void> updateLastLogin(
            @PathVariable UUID userId,
            @RequestParam("ipAddress") String ipAddress,
            LocalDateTime loginTime
    );

    /**
     * 获取用户信息
     */
    @GetMapping("/{userId}")
    ApiResponse<UserInfo> getUserInfo(@PathVariable UUID userId);

    /**
     * 查询用户角色
     */
    //todo 待完善
    @GetMapping("/find-roles-by-userId")
    ApiResponse<Set<String>> findRolesByUserId(@RequestParam("userId") UUID userId);

    /**
     * 查询用户权限
     */
    //todo 待完善
    @GetMapping("/find-permissions-by-userId")
    ApiResponse<Set<String>> findPermissionsByUserId(@RequestParam("userId") UUID userId);
}