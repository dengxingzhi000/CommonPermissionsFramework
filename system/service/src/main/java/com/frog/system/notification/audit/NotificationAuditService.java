package com.frog.system.notification.audit;

import com.frog.system.domain.entity.NotificationAuditLog;
import com.frog.system.mapper.NotificationAuditLogMapper;
import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import com.frog.system.notification.model.NotificationDeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationAuditService {
    private final NotificationAuditLogMapper auditLogMapper;

    public void record(NotificationCommand command,
                       NotificationChannel channel,
                       NotificationDeliveryStatus status,
                       String errorMessage) {
        if (command == null) {
            return;
        }
        NotificationAuditLog notificationAuditLog = NotificationAuditLog.builder()
                .id(UUID.randomUUID())
                .referenceId(command.getReferenceId())
                .channel(channel.name())
                .status(status.name())
                .subject(command.getSubject())
                .username(command.getUsername())
                .email(command.getEmail())
                .templateCode(command.getTemplateCode())
                .content(command.getContent())
                .variables(command.getVariables())
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            auditLogMapper.insert(notificationAuditLog);
        } catch (Exception ex) {
            log.warn("Failed to persist notification audit. refId={}, channel={}",
                    command.getReferenceId(), channel, ex);
        }
    }
}

