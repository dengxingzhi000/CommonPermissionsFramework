package com.frog.common.feign.client;

import com.frog.common.response.ApiResponse;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.feign.fallback.SystemServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * System服务 Feign客户端
 *
 * @author Deng
 * createData 2025/11/6 11:26
 * @version 1.0
 */
@FeignClient(
        name = "system-service",
        path = "/internal/users",
        fallbackFactory = SystemServiceClientFallbackFactory.class
)
public interface SystemServiceClient {
    /**
     * 根据用户名查询用户（登录时调用）
     */
    @GetMapping("/by-username/{username}")
    ApiResponse<SecurityUser> getUserByUsername(@PathVariable String username);

    /**
     * 查询用户权限（生成Token时调用）
     */
    @GetMapping("/{userId}/permissions")
    ApiResponse<Set<String>> getUserPermissions(@PathVariable UUID userId);

    /**
     * 查询用户角色（生成Token时调用）
     */
    @GetMapping("/{userId}/roles")
    ApiResponse<Set<String>> getUserRoles(@PathVariable UUID userId);

    /**
     * 更新最后登录信息（登录成功后调用）
     */
    @PostMapping("/{userId}/update-login")
    ApiResponse<Void> updateLastLogin(
            @PathVariable UUID userId,
            @RequestParam("ipAddress") String ipAddress
    );
}
