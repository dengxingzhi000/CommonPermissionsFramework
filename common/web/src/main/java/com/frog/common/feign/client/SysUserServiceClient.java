package com.frog.common.feign.client;

import com.frog.common.response.ApiResponse;
import com.frog.common.feign.fallback.UserServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * 用户服务Feign客户端
 * 用于服务间调用（Dubbo 降级备用方案）
 *
 * <p>架构说明：
 * - 主要通信：Dubbo (UserDubboService) - 高性能 RPC
 * - 降级备用：Feign (SysUserServiceClient) - HTTP REST
 *
 * <p>此客户端与 system-service 的 SysUserController 端点对应
 * <p>注意：认证相关方法（getUserByUsername, getUserRoles, getUserPermissions）
 * 应使用 Dubbo 而不是 Feign，因为它们在 controller 中不公开
 *
 * @author Deng
 * @version 2.0
 * createData 2025/10/31 9:50
 */
@FeignClient(
        name = "user-service",
        path = "/api/system/users",
        fallbackFactory = UserServiceClientFallbackFactory.class
)
public interface SysUserServiceClient {
    /**
     * 更新最后登录信息
     * 对应: SysUserController.updateLastLogin()
     * Dubbo: UserDubboService.updateLastLogin()
     */
    @GetMapping("/{userId}/update-login")
    ApiResponse<Void> updateLastLogin(
            @PathVariable UUID userId,
            @RequestParam("ipAddress") String ipAddress
    );
}