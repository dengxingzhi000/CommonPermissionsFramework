package com.frog.common.feign.fallback;

import com.frog.common.feign.factory.BaseFallbackFactory;
import com.frog.common.response.ApiResults;
import com.frog.common.feign.client.SysUserServiceClient;
import com.frog.common.dto.user.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
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
            public ApiResults<Void> updateLastLogin(UUID userId, String ipAddress) {
                log.warn("更新登录信息失败: userId={}, 原因: {}", userId, errorMsg);
                return ApiResults.success();
            }

            @Override
            public ApiResults<UserInfo> getUserInfo(UUID userId) {
                log.warn("获取用户信息失败: userId={}, 原因: {}", userId, errorMsg);
                return ApiResults.fail(503, "用户服务不可用");
            }

            @Override
            public ApiResults<Set<String>> findRolesByUserId(UUID userId) {
                log.warn("获取用户角色失败: userId={}, 原因: {}", userId, errorMsg);
                return ApiResults.fail(503, "用户服务不可用");
            }

            @Override
            public ApiResults<Set<String>> findPermissionsByUserId(UUID userId) {
                log.warn("获取用户权限失败: userId={}, 原因: {}", userId, errorMsg);
                return ApiResults.fail(503, "用户服务不可用");
            }
        };
    }
}