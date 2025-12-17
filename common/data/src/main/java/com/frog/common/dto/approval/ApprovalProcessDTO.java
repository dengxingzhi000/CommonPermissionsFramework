package com.frog.common.dto.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 *
 *
 * @author Deng
 * createData 2025/11/3 16:35
 * @version 1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "审批处理 DTO")
public class ApprovalProcessDTO {
    @Schema(description = "是否批准")
    @NotNull(message = "批准状态不能为空")
    private Boolean approved;

    @Schema(description = "拒绝理由")
    private String rejectReason;

    @Schema(description = "审批意见")
    private String comment;
}
