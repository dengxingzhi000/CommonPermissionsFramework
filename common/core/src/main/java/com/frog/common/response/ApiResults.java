package com.frog.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体
 *
 * @author Deng
 * createData 2025/10/11 14:28
 * @version 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResults<T>(
        int code,
        String message,
        T data,
        long timestamp
) {
    public static <T> ApiResults<T> success() {
        return success(null);
    }

    public static <T> ApiResults<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResults<T> success(String message, T data) {
        return new ApiResults<>(200, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResults<T> fail(int code, String message) {
        return new ApiResults<>(code, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResults<T> fail(ResultCode code) {
        return new ApiResults<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis());
    }
}
