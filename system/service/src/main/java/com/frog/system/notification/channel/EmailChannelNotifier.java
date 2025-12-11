package com.frog.system.notification.channel;

import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Simple email notifier placeholder. Integrate with actual provider when ready.
 */
@Component
@Slf4j
public class EmailChannelNotifier implements ChannelNotifier {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationCommand command) {
        if (command == null || !StringUtils.hasText(command.getEmail())) {
            log.debug("Skip email notification: missing recipient");
            return;
        }
        log.info("[Email] to={}, subject={}, template={}, ref={}, payload={}",
                command.getEmail(),
                command.getSubject(),
                command.getTemplateCode(),
                command.getReferenceId(),
                command.getVariables());
    }
}