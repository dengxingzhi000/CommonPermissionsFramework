package com.frog.common.security.idempotent;

import com.frog.common.exception.BusinessException;
import com.frog.common.web.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.UUID;

/**
 * 幂等性切面
 *
 * @author Deng
 * createData 2025/10/31 10:21
 * @version 1.0
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotentAspect {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExpressionParser parser = new SpelExpressionParser();

    private static final String TOKEN_HEADER = "Idempotent-Token";

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String idempotentKey = buildKey(point, idempotent);

        // 尝试获取锁
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", Duration.ofSeconds(idempotent.expireTime()));

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Duplicate request detected: {}", idempotentKey);
            throw new BusinessException(idempotent.message());
        }

        try {
            return point.proceed();
        } catch (Exception e) {
            // 如果业务执行失败，删除幂等性key，允许重试
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint point, Idempotent idempotent) {
        String prefix = idempotent.prefix();
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);

        return switch (idempotent.type()) {
            case TOKEN -> prefix + getTokenFromRequest();

            case PARAM -> {
                String keyExpression = idempotent.key();
                if (keyExpression.isEmpty()) {
                    throw new IllegalArgumentException("Key expression is required for PARAM type");
                }
                String paramKey = parseSpEL(point, keyExpression);
                yield prefix + userId + ":" + paramKey;
            }

            case PATH -> {
                String requestPath = getRequestPath();
                yield prefix + userId + ":" + requestPath;
            }
        };
    }


    private String getTokenFromRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new BusinessException("无法获取请求上下文");
        }

        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(TOKEN_HEADER);

        if (token == null || token.isEmpty()) {
            throw new BusinessException("缺少幂等性 Token");
        }

        return token;
    }

    private String getRequestPath() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();
        return request.getMethod() + ":" + request.getRequestURI();
    }

    private String parseSpEL(ProceedingJoinPoint point, String expression) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = point.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        Expression exp = parser.parseExpression(expression);
        Object value = exp.getValue(context);

        return value != null ? value.toString() : "";
    }
}
