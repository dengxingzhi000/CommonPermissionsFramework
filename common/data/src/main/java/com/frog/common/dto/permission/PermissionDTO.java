package com.frog.common.dto.permission;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 *
 *
 * @author Deng
 * createData 2025/10/15 11:45
 * @version 1.0
 */
@Data
public class PermissionDTO {
    private UUID id;

    @NotNull(message = "父级 ID不能为空")
    private UUID parentId;

    @NotBlank(message = "权限编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9:]*$", message = "权限编码只能包含小写字母、数字和冒号")
    private String permissionCode;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 64, message = "权限名称长度不能超过64位")
    private String permissionName;

    @NotNull(message = "权限类型不能为空")
    @Min(value = 1, message = "权限类型必须在1-5之间")
    @Max(value = 5, message = "权限类型必须在1-5之间")
    private Integer permissionType;

    private String routePath;

    private String component;

    private String redirect;

    private String icon;

    private String apiPath;

    private String httpMethod;

    @NotNull(message = "权限等级不能为空")
    @Min(value = 1, message = "权限等级必须在1-4之间")
    @Max(value = 4, message = "权限等级必须在1-4之间")
    private Integer permissionLevel;

    @NotNull(message = "风险等级不能为空")
    @Min(value = 1, message = "风险等级必须在1-4之间")
    @Max(value = 4, message = "风险等级必须在1-4之间")
    private Integer riskLevel;

    private Boolean needApproval;

    private Boolean needTwoFactor;

    private Integer sortOrder;

    private Boolean visible;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // 树形结构
    private List<PermissionDTO> children;
}
