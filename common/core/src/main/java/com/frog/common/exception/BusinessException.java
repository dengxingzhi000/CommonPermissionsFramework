package com.frog.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 *
 * @author Deng
 * createData 2025/10/15 14:26
 * @version 1.1 - Added cause-chaining constructors, merged from ServiceException
 */
@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
