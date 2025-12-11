package com.frog.common.trace.aspect;

import com.alibaba.fastjson2.JSON;
import com.frog.common.trace.annotation.BusinessTrace;
import com.frog.common.web.util.SecurityUtils;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class BusinessTraceAspect {
    private final Tracer tracer;
    private final ObservationRegistry observationRegistry;

    @Around("@annotation(businessTrace)")
    public Object around(ProceedingJoinPoint point, BusinessTrace businessTrace) throws Throwable {
        String operationName = resolveOperationName(point, businessTrace);
        Observation observation = Observation.createNotStarted(operationName, observationRegistry)
                .lowCardinalityKeyValue("operation", operationName)
                .start();
        Span span = tracer.spanBuilder(operationName).startSpan();

        enrichUser(span);
        if (businessTrace.recordArgs()) {
            Object[] args = point.getArgs();
            span.setAttribute("args.count", args == null ? 0 : args.length);
            span.setAttribute("args.payload", toJsonLimited(args, 2048));
        }

        long startNs = System.nanoTime();
        try (Observation.Scope ignored = observation.openScope(); Scope scope = span.makeCurrent()) {
            Object result = point.proceed();
            if (businessTrace.recordResult()) {
                span.setAttribute("result.payload", toJsonLimited(result, 4096));
            }
            return result;
        } catch (Throwable ex) {
            span.setAttribute("error.type", ex.getClass().getName());
            String msg = ex.getMessage();
            if (msg != null && !msg.isBlank()) {
                span.setAttribute("error.message", truncate(msg, 512));
            }
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, msg == null ? "" : truncate(msg, 256));
            throw ex;
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            span.setAttribute("duration.ms", elapsedMs);
            span.end();
            observation.stop();
        }
    }

    private String resolveOperationName(ProceedingJoinPoint point, BusinessTrace businessTrace) {
        String operationName = businessTrace.operationName();
        if (operationName == null || operationName.isBlank()) {
            MethodSignature sig = (MethodSignature) point.getSignature();
            operationName = sig.getDeclaringType().getSimpleName() + "." + sig.getMethod().getName();
        }
        return operationName;
    }

    private void enrichUser(Span span) {
        try {
            String username = SecurityUtils.getCurrentUsername().orElse(null);
            if (username != null && !username.isBlank()) {
                span.setAttribute("user", truncate(username, 128));
            }
        } catch (Throwable ignore) {
            // best effort
        }
    }

    private static String toJsonLimited(Object obj, int maxLen) {
        if (obj == null) {
            return "null";
        }
        try {
            String s = JSON.toJSONString(obj);
            return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
        } catch (Throwable t) {
            return "<json-error:" + t.getClass().getSimpleName() + ">";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

