package com.frog.common.response;

import lombok.Getter;

/**
 * 统一状态码枚举
 *
 * @author Deng
 * createData 2025/10/11 14:31
 * @version 1.0
 */
@Getter
public enum ResultCode {
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    VALIDATION_FAILED(422, "Validation Failed"),
    SERVER_ERROR(500, "Internal Server Error"),

    // 客户端错误 4xx
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // 服务端错误 5xx
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),
    GATEWAY_TIMEOUT(504, "网关超时"),

    // 业务错误 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_LOCKED(1003, "账户已被锁定"),
    USER_DISABLED(1004, "账户已被禁用"),
    USER_NOT_ACTIVATED(1005, "用户未激活"),
    USER_EXIST(1006, "用户已存在"),
    USER_CANNOT_DELETE_ADMIN(1007, "不能删除管理员用户"),
    USER_CANNOT_DELETE_SELF(1008, "不能删除自己"),
    USER_NEED_LOGIN(1009, "需要登录"),


    TOKEN_INVALID(1101, "Token 无效"),
    TOKEN_EXPIRED(1102, "Token 已过期"),
    TOKEN_BLACKLISTED(1103, "Token 已失效"),

    PERMISSION_DENIED(1201, "权限不足"),
    ROLE_NOT_FOUND(1202, "角色不存在"),

    // 服务间调用错误 2xxx
    FEIGN_ERROR(2001, "服务调用失败"),
    FEIGN_TIMEOUT(2002, "服务调用超时");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}