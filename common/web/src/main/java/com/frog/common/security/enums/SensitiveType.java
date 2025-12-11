package com.frog.common.security.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.Function;

/**
 *
 *
 * @author Deng
 * createData 2025/10/24 15:26
 * @version 1.0
 */
@Getter
@AllArgsConstructor
public enum SensitiveType {

    /**
     * 手机号脱敏：138****1234
     */
    MOBILE(s -> s.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2")),

    /**
     * 身份证号脱敏：110101********1234
     */
    ID_CARD(s -> s.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2")),

    /**
     * 邮箱脱敏：abc****@example.com
     */
    EMAIL(s -> s.replaceAll("(\\w{1,3})\\w*(@.*)", "$1****$2")),

    /**
     * 姓名脱敏：张**
     */
    NAME(s -> {
        if (s.length() <= 1) return "*";
        if (s.length() == 2) return s.charAt(0) + "*";
        return s.charAt(0) + "**";
    }),

    /**
     * 银行卡脱敏：6222 **** **** 1234
     */
    BANK_CARD(s -> s.replaceAll("(\\d{4})\\d*(\\d{4})", "$1 **** **** $2")),

    /**
     * 地址脱敏：保留前6位
     */
    ADDRESS(s -> s.length() <= 6 ? s : s.substring(0, 6) + "****");

    private final Function<String, String> desensitizer;

    public String desensitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return desensitizer.apply(value);
    }
}
