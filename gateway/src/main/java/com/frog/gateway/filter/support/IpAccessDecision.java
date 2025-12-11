package com.frog.gateway.filter.support;

public record IpAccessDecision(boolean allowed, String reason) {

    public static IpAccessDecision allow() {
        return new IpAccessDecision(true, "ALLOWED");
    }

    public static IpAccessDecision deny(String reason) {
        return new IpAccessDecision(false, reason);
    }
}
