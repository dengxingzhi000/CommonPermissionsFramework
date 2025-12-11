package com.frog.common.log.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 *
 * @author Deng
 * createData 2025/10/14 17:28
 * @version 1.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作描述
     */
    String operation() default "";

    /**
     * 业务类型
     */
    String businessType() default "";

    /**
     * 风险等级: 1-低, 2-中, 3-高, 4-极高
     */
    int riskLevel() default 1;

    /**
     * 是否记录请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean recordResult() default false;
}
