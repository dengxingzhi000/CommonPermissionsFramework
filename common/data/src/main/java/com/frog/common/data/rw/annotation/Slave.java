package com.frog.common.data.rw.annotation;

import java.lang.annotation.*;

/**
 * 强制走从库
 * <p>
 * 用于明确可以接受延迟的查询场景，如：
 * - 报表统计
 * - 批量导出
 * - 非实时查询
 *
 * @author Deng
 * @since 2025-12-16
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Slave {

    /**
     * 指定从库名称（可选，默认使用负载均衡选择）
     */
    String value() default "";

    /**
     * 是否允许降级到主库（从库不可用时）
     */
    boolean fallbackToMaster() default true;
}
