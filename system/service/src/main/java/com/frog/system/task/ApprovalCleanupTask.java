package com.frog.system.task;

import com.frog.system.domain.entity.SysPermissionApproval;
import com.frog.system.mapper.SysPermissionApprovalMapper;
import com.frog.system.mapper.SysUserRoleMapper;
import com.frog.system.notification.NotificationService;
import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
/**
 * 审批和临时权限清理定时任务
 *
 * @author Deng
 * createData 2025/11/3 16:00
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalCleanupTask {
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionApprovalMapper approvalMapper;
    private final NotificationService notificationService;

    /**
     * 每小时检查并更新过期的临时权限
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void updateExpiredTemporaryRoles() {
        log.info("Starting expired temporary roles update task");

        try {
            int updated = userRoleMapper.updateExpiredRolesStatus();
            if (updated > 0) {
                log.info("Updated {} expired temporary role assignments", updated);
            }
        } catch (Exception e) {
            log.error("Error updating expired temporary roles", e);
        }
    }

    /**
     * 每天凌晨2点检查并更新过期的临时授权审批
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void updateExpiredApprovals() {
        log.info("Starting expired approval update task");

        try {
            List<SysPermissionApproval> expiredApprovals =
                    approvalMapper.selectExpiredApprovals();

            if (!expiredApprovals.isEmpty()) {
                log.info("Found {} expired approvals", expiredApprovals.size());

                int updated = approvalMapper.updateExpiredApprovals();
                log.info("Updated {} expired approval records", updated);
            }
        } catch (Exception e) {
            log.error("Error updating expired approvals", e);
        }
    }

    /**
     * 每天上午9点检查即将过期的临时权限（提前7天通知）
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void notifyExpiringTemporaryRoles() {
        log.info("Starting expiring temporary roles notification task");

        try {
            List<Map<String, Object>> expiringRoles = userRoleMapper.findExpiringRolesForNotification(7);

            if (!expiringRoles.isEmpty()) {
                log.info("Found {} temporary roles expiring in 7 days", expiringRoles.size());

                for (Map<String, Object> role : expiringRoles) {
                    String username = (String) role.get("username");
                    String roleName = (String) role.get("role_name");
                    Object expireTime = role.get("expire_time");

                    log.info("Temporary role expiring soon: user={}, role={}, expireTime={}",
                            username, roleName, expireTime);

                    sendExpiringNotification(username, null, roleName, expireTime);
                }
            }
        } catch (Exception e) {
            log.error("Error sending expiring notifications", e);
        }
    }

    /**
     * 每天上午10点检查即将过期的审批（提前3天通知）
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void notifyExpiringApprovals() {
        log.info("Starting expiring approvals notification task");

        try {
            List<SysPermissionApproval> expiringApprovals =
                    approvalMapper.selectExpiringApprovals(3);

            if (!expiringApprovals.isEmpty()) {
                log.info("Found {} approvals expiring in 3 days", expiringApprovals.size());

                for (SysPermissionApproval approval : expiringApprovals) {
                    log.info("Approval expiring soon: id={}, type={}, expireTime={}",
                            approval.getId(), approval.getApprovalType(), approval.getExpireTime());

                    // TODO: 发送通知
                    sendApprovalExpiringNotification(approval);
                }
            }
        } catch (Exception e) {
            log.error("Error sending approval expiring notifications", e);
        }
    }

    /**
     * 每周一凌晨3点清理过期超过30天的数据（可选）
     */
    @Scheduled(cron = "0 0 3 ? * MON")
    public void cleanupExpiredData() {
        log.info("Starting cleanup of expired data");

        try {
            // 清理过期超过30天的临时角色记录
            int deletedRoles = userRoleMapper.deleteExpiredRoles();
            log.info("Cleaned up {} expired temporary role records", deletedRoles);

            // TODO: 清理过期的审批记录（如果需要）

        } catch (Exception e) {
            log.error("Error during expired data cleanup", e);
        }
    }

    /**
     * 发送即将过期通知
     */
    private void sendExpiringNotification(String username, String email,
                                          String roleName, Object expireTime) {
        String subject = "Temporary role expiring soon";
        String message = String.format("Hello %s, your temporary role %s will expire on %s.",
                username, roleName, expireTime);

        NotificationCommand command = NotificationCommand.builder()
                .referenceId("temp-role-expiring-" + roleName + "-" + username)
                .username(username)
                .email(email)
                .subject(subject)
                .content(message)
                .templateCode("temporary-role.expiring")
                .channel(NotificationChannel.EMAIL)
                .channel(NotificationChannel.SYSTEM_MESSAGE)
                .variable("username", username)
                .variable("roleName", roleName)
                .variable("expireTime", expireTime)
                .build();
        notificationService.send(command);
    }

    /**
     * 发送审批即将过期通知
     */
    private void sendApprovalExpiringNotification(SysPermissionApproval approval) {
        String subject = "Approval expiring soon";
        String message = String.format("Approval %s (type: %s) will expire on %s.",
                approval.getId(), approval.getApprovalType(), approval.getExpireTime());

        NotificationCommand command = NotificationCommand.builder()
                .referenceId("approval-expiring-" + approval.getId())
                .username("approver")
                .subject(subject)
                .content(message)
                .templateCode("approval.expiring")
                .channel(NotificationChannel.SYSTEM_MESSAGE)
                .variable("approvalId", approval.getId())
                .variable("approvalType", approval.getApprovalType())
                .variable("expireTime", approval.getExpireTime())
                .build();
        notificationService.send(command);
    }
}
