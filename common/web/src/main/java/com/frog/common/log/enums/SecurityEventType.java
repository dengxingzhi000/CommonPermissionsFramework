package com.frog.common.log.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 *
 * @author Deng
 * createData 2025/10/24 16:14
 * @version 1.0
 */
@Getter
@AllArgsConstructor
public enum SecurityEventType {

    LOGIN_SUCCESS("登录成功", 1),
    LOGIN_FAILED("登录失败", 2),
    LOGOUT("登出", 1),

    PASSWORD_CHANGED("密码修改", 2),
    PASSWORD_RESET("密码重置", 3),

    PERMISSION_GRANTED("权限授予", 3),
    PERMISSION_REVOKED("权限撤销", 3),

    DATA_EXPORT("数据导出", 4),
    DATA_DELETE("数据删除", 4),

    API_ABUSE("API 滥用", 4),
    SQL_INJECTION_ATTEMPT("SQL 注入尝试", 5),
    XSS_ATTEMPT("XSS 攻击尝试", 5),
    UNAUTHORIZED_ACCESS("未授权访问", 4),

    IP_BLACKLISTED("IP 被拉黑", 3),
    ACCOUNT_LOCKED("账户锁定", 3);

    private final String description;
    private final Integer riskLevel; // 1-低 2-中 3-高 4-严重 5-紧急
}
