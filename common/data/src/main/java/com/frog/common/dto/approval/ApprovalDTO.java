package com.frog.common.dto.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
/**
 *
 *
 * @author Deng
 * createData 2025/11/3 16:04
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "权限审批 DTO")
public class ApprovalDTO {
    @Schema(description = "审批 ID")
    private UUID id;

    @Schema(description = "申请人 ID")
    private UUID applicantId;

    @Schema(description = "申请类型:1-角色申请,2-权限申请,3-临时授权")
    private Integer approvalType;

    @Schema(description = "目标用户 ID")
    private UUID targetUserId;

    @Schema(description = "申请理由")
    private String applyReason;

    @Schema(description = "业务说明")
    private String businessJustification;

    @Schema(description = "审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝,4-已撤回")
    private Integer approvalStatus;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveTime;

    @Schema(description = "失效时间")
    private LocalDateTime expireTime;

    @Schema(description = "审批时间")
    private LocalDateTime approvedTime;

    @Schema(description = "拒绝理由")
    private String rejectReason;

    @Schema(description = "角色 ID列表")
    private List<UUID> roleIds;

    @Schema(description = "权限 ID列表")
    private List<UUID> permissionIds;
}
