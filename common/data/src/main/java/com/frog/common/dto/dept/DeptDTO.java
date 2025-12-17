package com.frog.common.dto.dept;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 部门 DTO
 *
 * @author Deng
 * createData 2025/11/7 11:15
 * @version 1.0
 */
@Data
public class DeptDTO {
    private UUID id;
    private UUID parentId;

    @NotBlank(message = "部门编码不能为空")
    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    private String deptName;

    private Integer deptType;
    private UUID leaderId;
    private String leaderName;
    private String phone;
    private String email;
    private Integer isolationLevel;
    private Integer sortOrder;
    private Integer status;

    // 树形结构
    private List<DeptDTO> children;

    // 统计信息
    private Integer userCount;
    private Integer childCount;
}
