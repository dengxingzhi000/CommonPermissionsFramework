package com.frog.common.feign.fallback;

import com.frog.common.feign.factory.BaseFallbackFactory;
import com.frog.common.response.ApiResponse;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.feign.client.SystemServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * System 服务降级工厂
 *
 * @author Deng
 * createData 2025/11/6 11:39
 * @version 1.0
 */
@Slf4j
@Component
public class SystemServiceClientFallbackFactory extends BaseFallbackFactory<SystemServiceClient> {

    @Override
    protected SystemServiceClient createFallback(String errorMsg, Throwable cause) {
        return new SystemServiceClient() {
            @Override
            public ApiResponse<SecurityUser> getUserByUsername(String username) {
                log.error("调用system-service查询用户失败: username={}, error={}",
                        username, errorMsg);
                // 降级策略：返回错误，阻止登录
                return ApiResponse.fail(503, "用户服务暂时不可用，请稍后再试");
            }

            @Override
            public ApiResponse<Set<String>> getUserPermissions(UUID userId) {
                log.error("调用system-service查询权限失败: userId={}, error={}",
                        userId, errorMsg);
                // 降级策略：返回空权限集合（安全优先）
                return ApiResponse.success(Set.of());
            }

            @Override
            public ApiResponse<Set<String>> getUserRoles(UUID userId) {
                log.error("调用system-service查询角色失败: userId={}, error={}",
                        userId, errorMsg);
                // 降级策略：返回空角色集合
                return ApiResponse.success(Set.of());
            }

            @Override
            public ApiResponse<Void> updateLastLogin(UUID userId, String ipAddress) {
                log.error("调用system-service更新登录信息失败: userId={}, error={}",
                        userId, errorMsg);
                // 降级策略：忽略更新失败，不影响登录流程
                return ApiResponse.success();
            }
        };
    }
}