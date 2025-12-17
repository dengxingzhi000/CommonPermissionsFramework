package com.frog.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frog.common.feign.client.SysAuthServiceClient;
import com.frog.common.log.annotation.AuditLog;
import com.frog.common.domain.PageResult;
import com.frog.common.response.ApiResponse;
import com.frog.common.dto.user.ChangePasswordRequest;
import com.frog.common.dto.role.TemporaryRoleGrantDTO;
import com.frog.common.dto.user.UserDTO;
import com.frog.common.web.util.SecurityUtils;
import com.frog.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户管理控制器
 *
 * @author Deng
 * createData 2025/10/14 18:00
 * @version 1.0
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@Tag(name = "用户模块")
public class SysUserController {
    private final ISysUserService userService;
    private final SysAuthServiceClient authServiceClient;

    /**
     * 查询用户列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    @Operation(summary = "查询用户列表")
    public ApiResponse<PageResult<UserDTO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) Integer status) {
        Page<UserDTO> result = userService.listUsers(page, size, username, status);

        return ApiResponse.success(PageResult.of(result));
    }

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<UserDTO> getById(@PathVariable UUID id) {
        UserDTO user = userService.getUserById(id);

        return ApiResponse.success(user);
    }

    /**
     * 新增用户
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:user:add')")
    @AuditLog(
            operation = "新增用户",
            businessType = "USER"
    )
    public ApiResponse<Void> add(@Validated @RequestBody UserDTO userDTO) {
        userService.addUser(userDTO);

        return ApiResponse.success();
    }

    /**
     * 修改用户
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(
            operation = "修改用户",
            businessType = "USER"
    )
    public ApiResponse<Void> update(@PathVariable UUID id,
                               @Validated @RequestBody UserDTO userDTO) {
        userDTO.setId(id);
        userService.updateUser(userDTO);

        return ApiResponse.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @AuditLog(
            operation = "删除用户",
            businessType = "USER",
            riskLevel = 4
    )
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        userService.deleteUser(id);

        return ApiResponse.success();
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());

        return ApiResponse.success();
    }

    /**
     * 重置密码
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('system:user:reset')")
    @AuditLog(
            operation = "重置密码",
            businessType = "USER",
            riskLevel = 3
    )
    public ApiResponse<String> resetPassword(@PathVariable UUID id) {
        String newPassword = userService.resetPassword(id);

        return ApiResponse.success(newPassword);
    }

    /**
     * 授权角色
     */
    @PostMapping("/{id}/grant-roles")
    @PreAuthorize("hasAuthority('system:user:grant')")
    @AuditLog(
            operation = "授权角色",
            businessType = "USER",
            riskLevel = 4
    )
    public ApiResponse<Void> grantRoles(@PathVariable UUID id,
                                   @RequestBody List<UUID> roleIds) {
        userService.grantRoles(id, roleIds);

        return ApiResponse.success();
    }

    /**
     * 锁定/解锁用户
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(
            operation = "锁定用户",
            businessType = "USER",
            riskLevel = 3
    )
    public ApiResponse<Void> lockUser(@PathVariable UUID id, @RequestParam Boolean lock) {
        userService.lockUser(id, lock);

        return ApiResponse.success();
    }

    /**
     * 强制用户下线
     */
    @PostMapping("/{id}/force-logout")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(
            operation = "强制下线",
            businessType = "USER",
            riskLevel = 3
    )
    public ApiResponse<Void> forceLogout(@PathVariable UUID id, @RequestParam String reason) {
        authServiceClient.forceLogout(id, reason);

        return ApiResponse.success();
    }

    /**
     * 授予临时角色
     */
    @PostMapping("/{id}/grant-temporary-roles")
    @PreAuthorize("hasAuthority('system:user:grant')")
    @AuditLog(
            operation = "授予临时角色",
            businessType = "USER",
            riskLevel = 4
    )
    @Operation(summary = "授予临时角色")
    public ApiResponse<String> grantTemporaryRoles(
            @PathVariable UUID id,
            @RequestBody @Validated TemporaryRoleGrantDTO dto) {
        userService.grantTemporaryRoles(
                id,
                dto.getRoleIds(),
                dto.getEffectiveTime(),
                dto.getExpireTime()
        );

        return ApiResponse.success("临时角色授予成功");
    }

    /**
     * 延长临时角色有效期
     */
    @PostMapping("/{userId}/extend-temporary-role/{roleId}")
    @PreAuthorize("hasAuthority('system:user:grant')")
    @AuditLog(
            operation = "延长临时角色",
            businessType = "USER",
            riskLevel = 3
    )
    @Operation(summary = "延长临时角色有效期")
    public ApiResponse<String> extendTemporaryRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime newExpireTime) {
        userService.extendTemporaryRole(userId, roleId, newExpireTime);

        return ApiResponse.success("临时角色有效期已延长");
    }

    /**
     * 终止临时角色
     */
    @PostMapping("/{userId}/terminate-temporary-role/{roleId}")
    @PreAuthorize("hasAuthority('system:user:grant')")
    @AuditLog(
            operation = "终止临时角色",
            businessType = "USER",
            riskLevel = 3
    )
    @Operation(summary = "终止临时角色")
    public ApiResponse<String> terminateTemporaryRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {
        userService.terminateTemporaryRole(userId, roleId);

        return ApiResponse.success("临时角色已终止");
    }

    /**
     * 查询用户的临时角色列表
     */
    @GetMapping("/{id}/temporary-roles")
    @PreAuthorize("hasAuthority('system:user:list')")
    @Operation(summary = "查询用户的临时角色")
    public ApiResponse<List<Map<String, Object>>> getUserTemporaryRoles(@PathVariable UUID id) {
        List<Map<String, Object>> roles = userService.getUserTemporaryRoles(id);

        return ApiResponse.success(roles);
    }

    /**
     * 查询用户统计信息
     */
    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasAuthority('system:user:list')")
    @Operation(summary = "查询用户统计信息")
    public ApiResponse<Map<String, Object>> getUserStatistics(@PathVariable UUID id) {
        Map<String, Object> stats = userService.getUserStatistics(id);

        return ApiResponse.success(stats);
    }

    /**
     * 更新最后登录信息
     */
    @GetMapping("/{userId}/update-login")
    @Operation(summary = "更新最后登录信息")
    public ApiResponse<Void> updateLastLogin(@PathVariable UUID userId,
                                             @RequestParam("ipAddress") String ipAddress) {
        userService.updateLastLogin(userId, ipAddress);

        return ApiResponse.success();
    }
}
