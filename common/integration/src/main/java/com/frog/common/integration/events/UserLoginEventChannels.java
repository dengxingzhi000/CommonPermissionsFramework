package com.frog.common.integration.events;

public final class UserLoginEventChannels {
    private UserLoginEventChannels() {}

    public static final String EXCHANGE = "user.login.exchange";
    public static final String QUEUE = "user.login.queue";
    public static final String ROUTING_KEY = "user.login.key";
}
