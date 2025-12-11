package com.frog.system.notification;

import com.frog.system.notification.model.NotificationCommand;

/**
 * Central notification orchestration service (email, in-app, etc).
 */
public interface NotificationService {

    void send(NotificationCommand command);
}

