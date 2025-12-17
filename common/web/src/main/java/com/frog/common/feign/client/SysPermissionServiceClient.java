package com.frog.common.feign.client;

import com.frog.common.dto.permission.PermissionDTO;
import com.frog.common.response.ApiResponse;
import com.frog.common.feign.fallback.PermissionServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
/**
 * 权限服务Feign客户端
 * 用于服务间调用
 *
 * @author Deng
 * createData 2025/11/6 15:29
 * @version 1.0
 */
@FeignClient(
        name = "permission-service",
        path = "/api/system/permissions",
        fallbackFactory = PermissionServiceClientFallbackFactory.class
)
public interface SysPermissionServiceClient {
    /**
     * 查询权限树
     */
    @GetMapping("/tree")
    ApiResponse<List<PermissionDTO>> getPermissionTree();

    /**
     * 查询用户权限
     */
    @GetMapping("/user/{userId}")
    ApiResponse<Set<String>> getUserPermissions(@PathVariable UUID userId);

    /**
     * 查询用户是否有指定权限
     */
    @GetMapping("/user/{userId}/has-permission")
    ApiResponse<Boolean> hasPermission(@PathVariable UUID userId,
                                       @RequestParam("permissionCode") String permissionCode);

    /**
     * 根据 ID获取权限详情
     */
    @GetMapping("/{id}")
    ApiResponse<PermissionDTO> getPermissionById(@PathVariable UUID id);

    /**
     * 根据 URL和HTTP方法查询权限
     */
    @GetMapping("/find-by-url")
    List<String> findPermissionsByUrl(@RequestParam("url") String url, @RequestParam("method") String method);

    /**
     * 根据用户 ID查询权限
     */
    @GetMapping("/find-by-userId")
    Set<String> findAllPermissionsByUserId(@RequestParam("userId") UUID userId);

    /**
     * 查询所有 API权限
     */
    //todo 待完善
    @GetMapping("/api")
    List<Map<String, Object>> findApiPermissions();
}