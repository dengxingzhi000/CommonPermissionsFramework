package com.frog.system.notification.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frog.system.domain.entity.NotificationAuditLog;
import com.frog.system.mapper.NotificationAuditLogMapper;
import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import com.frog.system.notification.model.NotificationDeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationAuditService {
    private final NotificationAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

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
                .variablesJson(serializeVariables(command.getVariables()))
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

    private String serializeVariables(Map<String, Object> variables) {
        if (CollectionUtils.isEmpty(variables)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            log.debug("Unable to serialize notification variables", e);
            return variables.toString();
        }
    }
}

