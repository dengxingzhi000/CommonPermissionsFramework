package com.frog.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.frog.common.mybatisPlus.handler.StringArrayTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 数据权限规则表
 *
 * @author Deng
 * @since 2025-12-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "sys_data_permission_rule", autoResultMap = true)
@Tag(
        name = "SysDataPermissionRule 对象",
        description = "数据权限规则表"
)
public class SysDataPermissionRule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键 ID")
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "规则类型:1-全部,2-自定义SQL,3-本部门,4-本部门及以下,5-仅本人")
    private Integer ruleType;

    @Schema(description = "规则配置(JSONB)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> ruleConfig;

    @Schema(description = "SQL 条件表达式")
    private String sqlCondition;

    @Schema(description = "可见字段列表")
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] visibleFields;

    @Schema(description = "可编辑字段列表")
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] editableFields;

    @Schema(description = "需脱敏字段列表")
    @TableField(typeHandler = StringArrayTypeHandler.class)
    private String[] maskedFields;

    @Schema(description = "优先级(数字越大优先级越高)")
    private Integer priority;

    @Schema(description = "状态:0-禁用,1-启用")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "创建人")
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private UUID createBy;

    @Schema(description = "更新时间")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "更新人")
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private UUID updateBy;

    @Schema(description = "逻辑删除")
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    /**
     * 规则类型枚举
     */
    @Getter
    public enum RuleType {
        ALL(1, "全部数据"),
        CUSTOM_SQL(2, "自定义 SQL"),
        DEPT(3, "本部门"),
        DEPT_AND_CHILDREN(4, "本部门及以下"),
        SELF(5, "仅本人");

        private final int code;
        private final String desc;

        RuleType(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }
    }
}
