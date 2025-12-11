package com.frog.common.exception;

/**
 *
 *
 * @author Deng
 * createData 2025/10/15 14:27
 * @version 1.0
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
