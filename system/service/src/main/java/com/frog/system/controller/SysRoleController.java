package com.frog.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.frog.common.domain.PageResult;
import com.frog.common.log.annotation.AuditLog;
import com.frog.common.response.ApiResponse;
import com.frog.common.dto.role.RoleDTO;
import com.frog.system.service.ISysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 角色管理控制器
 *
 * @author Deng
 * createData 2025/10/14 18:01
 * @version 1.0
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@Tag(name = "角色模块")
public class SysRoleController {
    private final ISysRoleService roleService;

    /**
     * 查询角色列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    @Operation(summary = "查询角色列表")
    public ApiResponse<PageResult<RoleDTO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size,
                                                 @RequestParam(required = false) String roleName) {
        Page<RoleDTO> result = roleService.listRoles(page, size, roleName);

        return ApiResponse.success(PageResult.of(result));
    }

    /**
     * 查询所有角色（用于下拉选择）
     */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('system:role:list')")
    @Operation(summary = "查询所有角色")
    public ApiResponse<List<RoleDTO>> listAll() {
        List<RoleDTO> roles = roleService.listAllRoles();

        return ApiResponse.success(roles);
    }

    /**
     * 新增角色
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:role:add')")
    @Operation(summary = "新增角色")
    @AuditLog(
            operation = "新增角色",
            businessType = "ROLE",
            riskLevel = 4
    )
    public ApiResponse<Void> add(@Validated @RequestBody RoleDTO roleDTO) {
        roleService.addRole(roleDTO);

        return ApiResponse.success();
    }

    /**
     * 修改角色
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Operation(summary = "修改角色")
    @AuditLog(
            operation = "修改角色",
            businessType = "ROLE",
            riskLevel = 4
    )
    public ApiResponse<Void> update(@PathVariable UUID id,
                               @Validated @RequestBody RoleDTO roleDTO) {
        roleDTO.setId(id);
        roleService.updateRole(roleDTO);

        return ApiResponse.success();
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @Operation(summary = "删除角色")
    @AuditLog(
            operation = "删除角色",
            businessType = "ROLE",
            riskLevel = 4
    )
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        roleService.deleteRole(id);

        return ApiResponse.success();
    }

    /**
     * 授权权限
     */
    @PostMapping("/{id}/grant-permissions")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @Operation(summary = "角色授权")
    @AuditLog(
            operation = "角色授权",
            businessType = "ROLE",
            riskLevel = 4
    )
    public ApiResponse<Void> grantPermissions(@PathVariable UUID id,
                                         @RequestBody List<UUID> permissionIds) {
        roleService.grantPermissions(id, permissionIds);

        return ApiResponse.success();
    }

    /**
     * 查询角色权限
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:list')")
    @Operation(summary = "查询角色权限")
    public ApiResponse<List<UUID>> getRolePermissions(@PathVariable UUID id) {
        List<UUID> permissionIds = roleService.getRolePermissionIds(id);

        return ApiResponse.success(permissionIds);
    }
}

