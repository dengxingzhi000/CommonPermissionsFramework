package com.frog.system.notification.channel;

import com.frog.system.notification.model.NotificationChannel;
import com.frog.system.notification.model.NotificationCommand;

/**
 * SPI for channel-specific notification delivery.
 */
public interface ChannelNotifier {

    NotificationChannel channel();

    void send(NotificationCommand command);
}

