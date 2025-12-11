package com.frog.common.security.annotation;

import java.lang.annotation.*;

/**
 *
 *
 * @author Deng
 * createData 2025/10/24 15:07
 * @version 1.0
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EncryptField {

    /**
     * 加密算法（预留，当前仅支持AES）
     */
    String algorithm() default "AES";
}
