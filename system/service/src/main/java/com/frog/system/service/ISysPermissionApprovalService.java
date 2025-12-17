package com.frog.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.frog.common.dto.approval.ApprovalDTO;
import com.frog.common.dto.approval.ApprovalProcessDTO;
import com.frog.system.domain.entity.SysPermissionApproval;

import java.util.UUID;

/**
 * <p>
 * 权限申请审批表 服务类
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
public interface ISysPermissionApprovalService extends IService<SysPermissionApproval> {

    /**
     * 提交审批申请，用于发起权限审批流程。
     *
     * @param dto 审批申请数据
     * @return 审批单 ID
     */
    UUID submitApproval(ApprovalDTO dto);

    /**
     * 处理审批流程（审核、通过/驳回等）。
     *
     * @param approvalId 审批单 ID
     * @param dto 审批处理数据（动作、意见等）
     */
    void processApproval(UUID approvalId, ApprovalProcessDTO dto);

    /**
     * 撤回我发起的审批申请。
     *
     * @param approvalId 审批单 ID
     */
    void withdrawApproval(UUID approvalId);

    /**
     * 查询我发起的审批申请列表。
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页数量
     * @return 我的审批申请分页列表
     */
    Page<ApprovalDTO> getMyApplications(Integer pageNum, Integer pageSize);

    /**
     * 获取审批单详情。
     *
     * @param approvalId 审批单 ID
     * @return 审批单详情
     */
    ApprovalDTO getApprovalDetail(UUID approvalId);

    /**
     * 查询待我处理的审批任务列表。
     *
     * @param pageNum 页码，从1开始
     * @param pageSize 每页数量
     * @return 待处理审批分页列表
     */
    Page<ApprovalDTO> getPendingApprovals(Integer pageNum, Integer pageSize);
}
