package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serial;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 权限申请审批表
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@TableName("sys_permission_approval")
@Tag(
        name="SysPermissionApproval 对象",
        description="权限申请审批表"
)
public class SysPermissionApproval implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "审批 ID")
    @TableId(value = "id", type = IdType.NONE)
    private UUID id;

    @Schema(description = "申请人 ID")
    @TableField("applicant_id")
    private UUID applicantId;

    @Schema(description = "申请类型:1-角色申请,2-权限申请,3-临时授权")
    @TableField("approval_type")
    private Integer approvalType;

    @Schema(description = "目标用户 ID")
    @TableField("target_user_id")
    private UUID targetUserId;

    @Schema(description = "角色ID列表(UUID数组)")
    @TableField("role_ids")
    private UUID[] roleIds;

    @Schema(description = "权限ID列表(UUID数组)")
    @TableField("permission_ids")
    private UUID[] permissionIds;

    @Schema(description = "生效时间")
    @TableField("effective_time")
    private LocalDateTime effectiveTime;

    @Schema(description = "失效时间")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    @Schema(description = "申请理由")
    @TableField("apply_reason")
    private String applyReason;

    @Schema(description = "业务说明")
    @TableField("business_justification")
    private String businessJustification;

    @Schema(description = "审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝,4-已撤回")
    @TableField("approval_status")
    private Integer approvalStatus;

    @Schema(description = "当前审批人 ID")
    @TableField("current_approver_id")
    private UUID currentApproverId;

    @Schema(description = "审批链(JSONB)")
    @TableField(value = "approval_chain", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private java.util.List<java.util.Map<String, Object>> approvalChain;

    @Schema(description = "最终审批人")
    @TableField("approved_by")
    private UUID approvedBy;

    @Schema(description = "审批时间")
    @TableField("approved_time")
    private LocalDateTime approvedTime;

    @Schema(description = "拒绝理由")
    @TableField("reject_reason")
    private String rejectReason;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 冗余字段 ====================

    @Schema(description = "申请人姓名（冗余字段）")
    @TableField("applicant_name")
    private String applicantName;

    @Schema(description = "申请人部门名称（冗余字段）")
    @TableField("applicant_dept_name")
    private String applicantDeptName;

    @Schema(description = "目标用户姓名（冗余字段）")
    @TableField("target_user_name")
    private String targetUserName;

    @Schema(description = "审批人姓名（冗余字段）")
    @TableField("approver_name")
    private String approverName;

    @Schema(description = "角色名称数组（冗余字段）")
    @TableField(value = "role_names", typeHandler = com.frog.common.mybatisPlus.handler.StringArrayTypeHandler.class)
    private String[] roleNames;

    @Schema(description = "权限名称数组（冗余字段）")
    @TableField(value = "permission_names", typeHandler = com.frog.common.mybatisPlus.handler.StringArrayTypeHandler.class)
    private String[] permissionNames;
}
