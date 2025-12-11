package com.frog.common.trace.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.concurrent.Callable;

public final class TraceUtils {
    private TraceUtils() {
    }

    public static String getTraceId() {
        Span span = Span.current();
        return span.getSpanContext().isValid() ? span.getSpanContext().getTraceId() : "";
    }

    public static Runnable wrapRunnable(Runnable runnable) {
        Context context = Context.current();
        return () -> {
            try (Scope scope = context.makeCurrent()) {
                runnable.run();
            }
        };
    }

    public static <V> Callable<V> wrapCallable(Callable<V> callable) {
        Context context = Context.current();
        return () -> {
            try (Scope scope = context.makeCurrent()) {
                return callable.call();
            }
        };
    }
}
