package com.frog.common.feign.fallback;

import com.frog.common.feign.factory.BaseFallbackFactory;
import com.frog.common.response.ApiResponse;
import com.frog.common.feign.client.SysUserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * UserService 降级处理
 *
 * @author Deng
 * @version 2.0
 * createData 2025/10/31 9:52
 */
@Slf4j
@Component
public class UserServiceClientFallbackFactory extends BaseFallbackFactory<SysUserServiceClient> {

    @Override
    protected SysUserServiceClient createFallback(String errorMsg, Throwable cause) {
        return new SysUserServiceClient() {
            @Override
            public ApiResponse<Void> updateLastLogin(UUID userId, String ipAddress) {
                log.warn("更新登录信息失败: userId={}, 原因: {}", userId, errorMsg);
                return ApiResponse.success();
            }
        };
    }
}