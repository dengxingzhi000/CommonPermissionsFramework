package com.frog.system.notification.channel;

import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * In-app/system messaging notifier.
 */
@Component
@Slf4j
public class SystemMessageChannelNotifier implements ChannelNotifier {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SYSTEM_MESSAGE;
    }

    @Override
    public void send(NotificationCommand command) {
        if (command == null || !StringUtils.hasText(command.getUsername())) {
            log.debug("Skip system message: missing username. refId={}", command != null ? command.getReferenceId() : null);
            return;
        }
        log.info("[SystemMessage] user={}, subject={}, ref={}, body={}", command.getUsername(),
                command.getSubject(), command.getReferenceId(), command.getContent());
    }
}

