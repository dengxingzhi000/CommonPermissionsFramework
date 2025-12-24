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
public record ApiResponse<T>(
        int code,
        String message,
        T data,
        long timestamp
) {
    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> fail(ResultCode code) {
        return new ApiResponse<>(code.getCode(), code.getMessage(), null, System.currentTimeMillis());
    }
}
