package com.frog.common.log.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.experimental.Accessors;
/**
 * <p>
 * 操作审计日志表
 * </p>
 *
 * @author author
 * @since 2025-10-14
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_audit_log")
@AllArgsConstructor
@NoArgsConstructor
@Tag(
        name="SysAuditLog 对象",
        description="操作审计日志表"
)
public class SysAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志 ID")
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    @Schema(description = "操作用户 ID")
    private UUID userId;

    @Schema(description = "操作用户名")
    private String username;

    @Schema(description = "操作人真实姓名")
    private String realName;

    @Schema(description = "所属部门 ID")
    private UUID deptId;

    @Schema(description = "操作类型:LOGIN,LOGOUT,ADD,UPDATE,DELETE,QUERY,EXPORT,APPROVE")
    private String operationType;

    @Schema(description = "操作模块")
    private String operationModule;

    @Schema(description = "操作描述")
    private String operationDesc;

    @Schema(description = "请求 URI")
    private String requestUri;

    @Schema(description = "请求方法")
    private String requestMethod;

    @Schema(description = "请求参数(敏感信息脱敏)")
    private String requestParams;

    @Schema(description = "响应数据(敏感信息脱敏)")
    private String responseData;

    @Schema(description = "响应状态码")
    private Integer responseStatus;

    @Schema(description = "操作 IP")
    private String ipAddress;

    @Schema(description = "IP 所在地")
    private String location;

    @Schema(description = "浏览器信息")
    private String userAgent;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务 ID")
    private String businessId;

    @Schema(description = "旧值(JSON)")
    private String oldValue;

    @Schema(description = "新值(JSON)")
    private String newValue;

    @Schema(description = "风险等级:1-低,2-中,3-高,4-极高")
    private Integer riskLevel;

    @Schema(description = "状态:0-失败,1-成功")
    private Integer status;

    @Schema(description = "错误信息")
    private String errorMsg;

    @Schema(description = "执行时长(毫秒)")
    private Integer executeTime;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
