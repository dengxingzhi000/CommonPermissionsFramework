package com.frog.common.data.rw.aop;

import com.frog.common.data.rw.annotation.Master;
import com.frog.common.data.rw.annotation.Slave;
import com.frog.common.data.rw.routing.ReadWriteRoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * 读写分离路由切面
 * <p>
 * 处理 @Master、@Slave 注解，以及自动判断事务类型
 * <p>
 * 优先级高于 @Transactional，确保在事务开启前设置路由
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadWriteRoutingAspect {
    @Pointcut("@annotation(com.frog.common.data.rw.annotation.Master)")
    public void masterPointcut() {}

    @Pointcut("@annotation(com.frog.common.data.rw.annotation.Slave)")
    public void slavePointcut() {}

    @Pointcut("@within(com.frog.common.data.rw.annotation.Master)")
    public void masterClassPointcut() {}

    @Pointcut("@within(com.frog.common.data.rw.annotation.Slave)")
    public void slaveClassPointcut() {}

    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalPointcut() {}

    /**
     * 处理 @Master 注解
     */
    @Around("masterPointcut() || masterClassPointcut()")
    public Object aroundMaster(ProceedingJoinPoint joinPoint) throws Throwable {
        Master master = getAnnotation(joinPoint, Master.class);
        String reason = master != null ? master.reason() : "";

        log.debug("[RW-Routing] @Master intercepted: {}.{}, reason: {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                reason);

        ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.MASTER);
        try {
            return joinPoint.proceed();
        } finally {
            ReadWriteRoutingContext.pop();
        }
    }

    /**
     * 处理 @Slave 注解
     */
    @Around("slavePointcut() || slaveClassPointcut()")
    public Object aroundSlave(ProceedingJoinPoint joinPoint) throws Throwable {
        Slave slave = getAnnotation(joinPoint, Slave.class);

        String slaveName = slave != null ? slave.value() : "";
        if (!slaveName.isEmpty()) {
            ReadWriteRoutingContext.specifySlave(slaveName);
        }

        log.debug("[RW-Routing] @Slave intercepted: {}.{}, slave: {}",
                joinPoint.getTarget().getClass().getSimpleName(),
                joinPoint.getSignature().getName(),
                slaveName.isEmpty() ? "auto" : slaveName);

        ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.SLAVE);
        try {
            return joinPoint.proceed();
        } finally {
            ReadWriteRoutingContext.pop();
        }
    }

    /**
     * 处理 @Transactional 注解
     * <p>
     * - readOnly=true → 从库
     * - readOnly=false → 主库
     */
    @Around("transactionalPointcut()")
    public Object aroundTransactional(ProceedingJoinPoint joinPoint) throws Throwable {
        Transactional transactional = getAnnotation(joinPoint, Transactional.class);

        // 如果已经有显式路由，不再处理
        if (ReadWriteRoutingContext.current() != ReadWriteRoutingContext.RoutingType.AUTO) {
            return joinPoint.proceed();
        }

        if (transactional != null && transactional.readOnly()) {
            log.debug("[RW-Routing] @Transactional(readOnly=true) intercepted: {}.{}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName());

            ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.SLAVE);
            try {
                return joinPoint.proceed();
            } finally {
                ReadWriteRoutingContext.pop();
            }
        } else {
            // 写事务，记录写操作时间
            ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.MASTER);
            try {
                Object result = joinPoint.proceed();
                // 事务成功后标记写操作
                ReadWriteRoutingContext.markWrite();
                return result;
            } finally {
                ReadWriteRoutingContext.pop();
            }
        }
    }

    private <T extends Annotation> T getAnnotation(
            ProceedingJoinPoint joinPoint, Class<T> annotationClass) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 先从方法上找
        T annotation = method.getAnnotation(annotationClass);
        if (annotation != null) {
            return annotation;
        }

        // 再从类上找
        return joinPoint.getTarget().getClass().getAnnotation(annotationClass);
    }
}
