package com.frog.common.feign.fallback;

import com.frog.common.feign.client.SysAuthServiceClient;
import com.frog.common.feign.factory.BaseFallbackFactory;
import com.frog.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SysAuthService 降级处理
 *
 * @author Deng
 * createData 2025/11/6 13:47
 * @version 1.0
 */
@Slf4j
@Component
public class SysAuthServiceClientFallbackFactory extends BaseFallbackFactory<SysAuthServiceClient> {

    @Override
    protected SysAuthServiceClient createFallback(String errorMsg, Throwable cause) {
        return new SysAuthServiceClient() {
            @Override
            public ApiResponse<Void> forceLogout(UUID userId, String reason) {
                log.error("调用认证服务强制登出失败: userId={}, reason={}, 错误信息: {}", userId, reason, errorMsg);
                return ApiResponse.fail(503, "认证服务暂时不可用");
            }
        };
    }
}
