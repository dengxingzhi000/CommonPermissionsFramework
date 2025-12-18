package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 临时权限表 - 用于临时授权
 *
 * @author Deng
 * @since 2025-12-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_temp_permission")
@Tag(
        name = "SysTempPermission 对象",
        description = "临时权限表"
)
public class SysTempPermission implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "用户 ID（跨库关联 db_user.sys_user）")
    @TableField("user_id")
    private UUID userId;

    @Schema(description = "权限 ID")
    @TableField("permission_id")
    private UUID permissionId;

    @Schema(description = "审批 ID（跨库关联 db_approval.sys_permission_approval）")
    @TableField("approval_id")
    private UUID approvalId;

    @Schema(description = "生效时间")
    @TableField("effective_time")
    private LocalDateTime effectiveTime;

    @Schema(description = "过期时间")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    @Schema(description = "状态:0-禁用,1-启用")
    @TableField("status")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "创建人")
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private UUID createBy;

    /**
     * 判断是否在有效期内
     */
    public boolean isEffective() {
        if (status == null || status != 1) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(effectiveTime) && now.isBefore(expireTime);
    }
}