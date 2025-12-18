package com.frog.common.feign.client;

import com.frog.common.feign.fallback.SysAuthServiceClientFallbackFactory;
import com.frog.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * SysAuth 服务Feign客户端
 *
 * @author Deng
 * createData 2025/11/6 13:37
 * @version 1.0
 */
@FeignClient(
        name = "auth-service",
        path = "/api/auth",
        fallbackFactory = SysAuthServiceClientFallbackFactory.class
)
public interface SysAuthServiceClient {
    /**
     * 强制用户登出
     */
    @PostMapping("/force-logout/{userId}")
    ApiResponse<Void> forceLogout(@PathVariable UUID userId, @RequestParam("reason") String reason);
}
