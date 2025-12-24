package com.frog.common.feign.fallback;


import com.frog.common.dto.permission.ApiPermissionDTO;
import com.frog.common.dto.permission.PermissionDTO;
import com.frog.common.feign.client.SysPermissionServiceClient;
import com.frog.common.feign.factory.BaseFallbackFactory;
import com.frog.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 权限服务 Feign 客户端降级工厂
 *
 * <p>SECURITY: Implements fail-closed pattern for permission checks
 * - Permission lookup methods throw AccessDeniedException on service failure
 * - Other query methods return safe defaults (empty collections)
 *
 * <p>Sentinel 熔断触发后会调用此降级方法
 *
 * @author Deng
 * createData 2025/11/6 15:30
 */
@Slf4j
@Component
public class PermissionServiceClientFallbackFactory extends BaseFallbackFactory<SysPermissionServiceClient> {

    @Override
    protected SysPermissionServiceClient createFallback(String errorMsg, Throwable cause) {
        return new SysPermissionServiceClient() {
            @Override
            public ApiResponse<List<PermissionDTO>> getPermissionTree() {
                log.error("调用权限服务查询权限树失败: {}", errorMsg, cause);
                return ApiResponse.success(new ArrayList<>());
            }

            @Override
            public ApiResponse<Set<String>> getUserPermissions(UUID userId) {
                log.error("SECURITY: User permission lookup failed via Sentinel fallback - DENYING ACCESS. " +
                         "userId={}, error: {}", userId, errorMsg, cause);
                return ApiResponse.success(Collections.emptySet());
            }

            @Override
            public ApiResponse<PermissionDTO> getPermissionById(UUID id) {
                log.error("调用权限服务查询权限详情失败: id={}, 原因: {}", id, errorMsg, cause);
                return ApiResponse.fail(503, "权限服务暂时不可用");
            }

            @Override
            public List<String> findPermissionsByUrl(String url, String method) {
                log.error("SECURITY: Permission lookup by URL failed via Sentinel fallback - DENYING ACCESS. " +
                         "url={}, method={}, error: {}", url, method, errorMsg, cause);
                // FAIL-CLOSED: Throw exception to deny access when permission check fails
                throw new AccessDeniedException(
                    "Permission service unavailable (Sentinel circuit open) - access denied as safety measure");
            }

            @Override
            public List<ApiPermissionDTO> findApiPermissions() {
                log.error("调用权限服务查询API权限失败: 错误信息: {}", errorMsg, cause);
                return Collections.emptyList();
            }
        };
    }
}
