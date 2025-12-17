package com.frog.common.sentinel.aspect;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.frog.common.sentinel.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Sentinel 注解切面实现
 *
 * @author Deng
 * createData 2025/10/21 16:13
 * @version 1.0
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String resourceName = rateLimit.value();
        if (resourceName.isEmpty()) {
            MethodSignature signature = (MethodSignature) point.getSignature();
            resourceName = signature.getDeclaringTypeName() + "." + signature.getName();
        }

        try (Entry entry = SphU.entry(resourceName)) {
            return point.proceed();
        } catch (BlockException ex) {
            log.warn("Rate limit triggered: {}", resourceName);
            throw ex;
        }
    }
}
