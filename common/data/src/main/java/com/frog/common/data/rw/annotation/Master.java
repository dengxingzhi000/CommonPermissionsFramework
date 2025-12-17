package com.frog.common.data.rw.annotation;

import java.lang.annotation.*;

/**
 * 强制走主库
 * <p>
 * 用于需要读取最新数据的场景，如：
 * - 写后立即读
 * - 关键业务查询
 * - 事务中的读操作
 *
 * @author Deng
 * @since 2025-12-16
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Master {

    /**
     * 原因说明（用于日志和监控）
     */
    String reason() default "";
}
