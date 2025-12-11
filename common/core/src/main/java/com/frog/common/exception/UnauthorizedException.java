package com.frog.common.exception;

/**
 *
 *
 * @author Deng
 * createData 2025/10/15 14:28
 * @version 1.0
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
