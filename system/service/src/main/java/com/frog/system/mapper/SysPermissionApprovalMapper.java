package com.frog.system.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.frog.system.domain.entity.SysPermissionApproval;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

/**
 * <p>
 * 权限申请审批表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2025-10-30
 */
@Mapper
@DS("approval")
public interface SysPermissionApprovalMapper extends BaseMapper<SysPermissionApproval> {

    /**
     * 分页查询审批列表
     */
    @Select("""
            <script>
            SELECT * FROM sys_permission_approval
            WHERE 1=1
            <if test='applicantId != null and applicantId != ""'>
                AND applicant_id = #{applicantId}
            </if>
            <if test='approvalStatus != null'>
                AND approval_status = #{approvalStatus}
            </if>
            <if test='approvalType != null'>
                AND approval_type = #{approvalType}
            </if>
            ORDER BY create_time DESC
            </script>
            """)
    Page<SysPermissionApproval> selectApprovalPage(
            Page<SysPermissionApproval> page,
            @Param("applicantId") UUID applicantId,
            @Param("approvalStatus") Integer approvalStatus,
            @Param("approvalType") Integer approvalType
    );

    /**
     * 查询待我审批的申请列表
     */
    @Select("""
            SELECT * FROM sys_permission_approval
            WHERE current_approver_id = #{approverId}
            AND approval_status IN (0, 1)
            ORDER BY create_time ASC
            """)
    Page<SysPermissionApproval> selectPendingApprovals(
            Page<SysPermissionApproval> page,
            @Param("approverId") UUID approverId
    );

    /**
     * 查询我处理过的审批
     */
    @Select("""
            SELECT * FROM sys_permission_approval
            WHERE approved_by = #{approverId}
            AND approval_status IN (2, 3)
            ORDER BY approved_time DESC
            """)
    Page<SysPermissionApproval> selectProcessedApprovals(
            Page<SysPermissionApproval> page,
            @Param("approverId") UUID approverId
    );

    /**
     * 查询用户的申请历史
     */
    @Select("""
            SELECT * FROM sys_permission_approval
            WHERE applicant_id = #{userId}
            ORDER BY create_time DESC
            """)
    Page<SysPermissionApproval> selectUserApplyHistory(
            Page<SysPermissionApproval> page,
            @Param("userId") UUID userId
    );

    /**
     * 查询即将过期的临时权限（用于提醒）
     */
    @Select("""
            SELECT * FROM sys_permission_approval
            WHERE approval_type = 3
            AND approval_status = 2
            AND expire_time IS NOT NULL
            AND expire_time BETWEEN NOW() AND NOW() + make_interval(days => #{days})
            ORDER BY expire_time ASC
            """)
    List<SysPermissionApproval> selectExpiringApprovals(@Param("days") Integer days);

    /**
     * 查询已过期的临时权限
     */
    @Select("""
            SELECT * FROM sys_permission_approval
            WHERE approval_type = 3
            AND approval_status = 2
            AND expire_time < NOW()
            """)
    List<SysPermissionApproval> selectExpiredApprovals();

    /**
     * 统计用户待审批数量
     */
    @Select("""
            SELECT COUNT(*) FROM sys_permission_approval
            WHERE current_approver_id = #{approverId}
            AND approval_status IN (0, 1)
            """)
    Integer countPendingApprovals(@Param("approverId") UUID approverId);

    /**
     * 统计用户的申请数量（按状态）
     */
    @Select("""
            SELECT COUNT(*) FROM sys_permission_approval
            WHERE applicant_id = #{userId}
            AND approval_status = #{status}
            """)
    Integer countUserApplications(
            @Param("userId") UUID userId,
            @Param("status") Integer status
    );

    /**
     * 更新审批状态
     */
    @Update("""
            UPDATE sys_permission_approval
            SET approval_status = #{status},
                approved_by = #{approverId},
                approved_time = NOW(),
                reject_reason = #{rejectReason},
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateApprovalStatus(
            @Param("id") UUID id,
            @Param("status") Integer status,
            @Param("approverId") UUID approverId,
            @Param("rejectReason") String rejectReason
    );

    /**
     * 更新当前审批人
     */
    @Update("""
            UPDATE sys_permission_approval
            SET current_approver_id = #{approverId},
                approval_status = 1,
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateCurrentApprover(
            @Param("id") UUID id,
            @Param("approverId") UUID approverId
    );

    /**
     * 更新审批链
     * 注意：approvalChain 参数应为 JSON 格式字符串，将被转换为 JSONB 类型存储
     */
    @Update("""
            UPDATE sys_permission_approval
            SET approval_chain = #{approvalChain}::jsonb,
                update_time = NOW()
            WHERE id = #{id}
            """)
    int updateApprovalChain(
            @Param("id") UUID id,
            @Param("approvalChain") String approvalChain
    );

    /**
     * 检查用户是否有相同的待审批申请
     */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_permission_approval
            WHERE applicant_id = #{applicantId}
            AND target_user_id = #{targetUserId}
            AND approval_type = #{approvalType}
            AND approval_status IN (0, 1)
            """)
    boolean existsPendingApplication(
            @Param("applicantId") UUID applicantId,
            @Param("targetUserId") UUID targetUserId,
            @Param("approvalType") Integer approvalType
    );

    /**
     * 批量更新过期的临时权限状态
     */
    @Update("""
            UPDATE sys_permission_approval
            SET approval_status = 5
            WHERE approval_type = 3
            AND approval_status = 2
            AND expire_time < NOW()
            """)
    int updateExpiredApprovals();

    // 注意：findFirstUserByRoleCode 方法已移至 Service 层实现
    // 该方法涉及跨库查询（user + permission），需要通过 Service 层聚合：
    // 1. 先通过 SysRoleMapper 查询 roleCode 对应的 roleId
    // 2. 再通过 SysUserRoleMapper 查询该角色的 userId 列表
    // 3. 最后通过 SysUserMapper 查询第一个有效用户
}
