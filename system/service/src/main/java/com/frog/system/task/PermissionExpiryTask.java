package com.frog.system.task;

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
 * 权限过期检查定时任务
 *
 * @author Deng
 * createData 2025/10/30 14:50
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionExpiryTask {
    private final SysUserRoleMapper userRoleMapper;
    private final NotificationService notificationService;

    /**
     * 每天凌晨2点检查并处理过期权限
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkExpiredPermissions() {
        log.info("Starting expired permissions check task");

        try {
            // 1. 查询已过期的角色
            List<Map<String, Object>> expiredRoles = userRoleMapper.findExpiredRolesForCleanup();

            if (!expiredRoles.isEmpty()) {
                log.warn("Found {} expired roles", expiredRoles.size());

                // 记录过期信息
                for (Map<String, Object> role : expiredRoles) {
                    log.info("Expired role: user={}, role={}, expireTime={}",
                            role.get("username"),
                            role.get("role_name"),
                            role.get("expire_time"));
                }

                // 2. 更新过期角色状态（不直接删除，便于审计）
                int updatedCount = userRoleMapper.updateExpiredRolesStatus();
                log.info("Updated {} expired role assignments", updatedCount);

                // 3. 发送过期通知（TODO: 集成邮件/短信服务）
                sendExpiryNotifications(expiredRoles);
            }

            log.info("Expired permissions check completed");

        } catch (Exception e) {
            log.error("Error during expired permissions check", e);
        }
    }

    /**
     * 每天上午9点检查即将过期的权限（提前7天通知）
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void checkExpiringPermissions() {
        log.info("Starting expiring permissions check task");

        try {
            // 查询7天内即将过期的角色
            List<Map<String, Object>> expiringRoles = userRoleMapper.findExpiringRolesForNotification(7);

            if (!expiringRoles.isEmpty()) {
                log.info("Found {} roles expiring in 7 days", expiringRoles.size());

                // 发送即将过期通知
                for (Map<String, Object> role : expiringRoles) {
                    log.info("Role expiring soon: user={}, role={}, expireTime={}",
                            role.get("username"),
                            role.get("role_name"),
                            role.get("expire_time"));

                    // TODO: 发送通知邮件
                    sendExpiringNotification(role);
                }
            }

            log.info("Expiring permissions check completed");

        } catch (Exception e) {
            log.error("Error during expiring permissions check", e);
        }
    }

    /**
     * 每周一凌晨3点清理过期权限数据（可选）
     * 如果不需要保留过期数据用于审计，可以启用此任务
     */
    @Scheduled(cron = "0 0 3 ? * MON")
    public void cleanupExpiredPermissions() {
        log.info("Starting cleanup of expired permissions");

        try {
            // 删除过期超过30天的角色分配记录
            int deletedCount = userRoleMapper.deleteExpiredRoles();
            log.info("Cleaned up {} expired role assignments", deletedCount);

        } catch (Exception e) {
            log.error("Error during expired permissions cleanup", e);
        }
    }

    /**
     * 发送过期通知
     * TODO: 集成实际的通知服务（邮件/短信/站内信）
     */
    private void sendExpiryNotifications(List<Map<String, Object>> expiredRoles) {
        for (Map<String, Object> role : expiredRoles) {
            try {
                String username = (String) role.get("username");
                String email = (String) role.get("email");
                String roleName = (String) role.get("role_name");

                log.info("Sending expiry notification to user: {}, role: {}", username, roleName);
                String subject = "Permission expired";
                String body = String.format("Hello %s, your assigned role %s has expired.", username, roleName);

                NotificationCommand command = NotificationCommand.builder()
                        .referenceId("permission-expired-" + roleName + "-" + username)
                        .username(username)
                        .email(email)
                        .subject(subject)
                        .content(body)
                        .templateCode("permission.expired")
                        .channel(NotificationChannel.EMAIL)
                        .channel(NotificationChannel.SYSTEM_MESSAGE)
                        .variable("username", username)
                        .variable("roleName", roleName)
                        .build();
                notificationService.send(command);
            } catch (Exception e) {
                log.error("Failed to send expiry notification", e);
            }
        }
    }

    /**
     * 发送即将过期通知
     */
    private void sendExpiringNotification(Map<String, Object> role) {
        try {
            String username = (String) role.get("username");
            String email = (String) role.get("email");
            String roleName = (String) role.get("role_name");
            Object expireTime = role.get("expire_time");

            log.info("Sending expiring notification to user: {}, role: {}, expireTime: {}",
                    username, roleName, expireTime);
            String subject = "Permission expiring soon";
            String message = String.format("Hello %s, your role %s will expire on %s. Please renew if needed.",
                    username, roleName, expireTime);

            NotificationCommand command = NotificationCommand.builder()
                    .referenceId("permission-expiring-" + roleName + "-" + username)
                    .username(username)
                    .email(email)
                    .subject(subject)
                    .content(message)
                    .templateCode("permission.expiring")
                    .channel(NotificationChannel.EMAIL)
                    .channel(NotificationChannel.SYSTEM_MESSAGE)
                    .variable("username", username)
                    .variable("roleName", roleName)
                    .variable("expireTime", expireTime)
                    .build();
            notificationService.send(command);

        } catch (Exception e) {
            log.error("Failed to send expiring notification", e);
        }
    }
}
