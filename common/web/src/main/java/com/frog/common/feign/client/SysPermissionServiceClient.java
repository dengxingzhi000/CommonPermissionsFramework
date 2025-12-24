package com.frog.common.feign.client;

import com.frog.common.dto.permission.ApiPermissionDTO;
import com.frog.common.dto.permission.PermissionDTO;
import com.frog.common.response.ApiResponse;
import com.frog.common.feign.fallback.PermissionServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.UUID;
/**
 * 权限服务Feign客户端
 * 用于服务间调用（Dubbo 降级备用方案）
 *
 * <p>架构说明：
 * - 主要通信：Dubbo (PermissionDubboService) - 高性能 RPC
 * - 降级备用：Feign (SysPermissionServiceClient) - HTTP REST
 *
 * <p>此客户端与 system-service 的 SysPermissionController 端点对应
 *
 * @author Deng
 * @version 2.0
 * createData 2025/11/6 15:29
 */
@FeignClient(
        name = "permission-service",
        path = "/api/system/permissions",
        fallbackFactory = PermissionServiceClientFallbackFactory.class
)
public interface SysPermissionServiceClient {
    /**
     * 查询权限树
     * 对应: SysPermissionController.tree()
     */
    @GetMapping("/tree")
    ApiResponse<List<PermissionDTO>> getPermissionTree();

    /**
     * 查询用户权限（用于 FeignPermissionAccess）
     * 对应: SysPermissionController.getUserPermissions()
     * Dubbo: PermissionDubboService.findAllPermissionsByUserId()
     */
    @GetMapping("/user/{userId}")
    ApiResponse<Set<String>> getUserPermissions(@PathVariable UUID userId);

    /**
     * 根据 ID获取权限详情
     * 对应: SysPermissionController.getById()
     */
    @GetMapping("/{id}")
    ApiResponse<PermissionDTO> getPermissionById(@PathVariable UUID id);

    /**
     * 根据 URL和HTTP方法查询权限（用于 FeignPermissionAccess）
     * 对应: SysPermissionController.findPermissionsByUrl()
     * Dubbo: PermissionDubboService.findPermissionsByUrl()
     */
    @GetMapping("/find-by-url")
    List<String> findPermissionsByUrl(@RequestParam("url") String url, @RequestParam("method") String method);

    /**
     * 查询所有 API权限（用于 DynamicPermissionLoader）
     * 对应: SysPermissionController.findApiPermissions()
     *
     * @return API权限列表，包含路径、HTTP方法和权限编码
     */
    @GetMapping("/api")
    List<ApiPermissionDTO> findApiPermissions();
}