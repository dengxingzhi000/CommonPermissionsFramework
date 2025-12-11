package com.frog.common.redis.lock;

import java.time.Duration;

public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String lockKey, Duration waitTime) {
        super("Failed to acquire lock '" + lockKey + "' within " + waitTime);
    }
}

