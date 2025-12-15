package com.frog.system.notification;

import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central notification orchestration service (email, in-app, etc).
 */
public interface NotificationService {

    /**
     * 发送通知（完整命令）
     */
    void send(NotificationCommand command);

    /**
     * 发送通知（便捷方法）
     *
     * @param username     用户名（用于站内消息）
     * @param email        邮箱（用于邮件通知）
     * @param templateCode 模板编码
     * @param subject      主题
     * @param variables    模板变量
     */
    default void sendNotification(String username, String email, String templateCode,
                                  String subject, Map<String, Object> variables) {
        NotificationCommand.NotificationCommandBuilder builder = NotificationCommand.builder()
                .referenceId(UUID.randomUUID().toString())
                .templateCode(templateCode)
                .subject(subject)
                .username(username)
                .email(email);

        if (variables != null) {
            variables.forEach(builder::variable);
        }

        // 默认发送站内消息和邮件
        builder.channel(NotificationChannel.SYSTEM_MESSAGE);
        if (email != null && !email.isEmpty()) {
            builder.channel(NotificationChannel.EMAIL);
        }

        send(builder.build());
    }

    /**
     * 发送通知到指定渠道
     */
    default void sendNotification(String username, String email, String templateCode,
                                  String subject, Map<String, Object> variables,
                                  Set<NotificationChannel> channels) {
        NotificationCommand.NotificationCommandBuilder builder = NotificationCommand.builder()
                .referenceId(UUID.randomUUID().toString())
                .templateCode(templateCode)
                .subject(subject)
                .username(username)
                .email(email);

        if (variables != null) {
            variables.forEach(builder::variable);
        }

        if (channels != null) {
            channels.forEach(builder::channel);
        }

        send(builder.build());
    }
}

