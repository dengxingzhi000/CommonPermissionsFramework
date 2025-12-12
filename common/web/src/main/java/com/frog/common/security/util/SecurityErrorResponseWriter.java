package com.frog.common.security.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Helper to emit structured JSON error responses with trace correlation.
 */
@UtilityClass
public class SecurityErrorResponseWriter {

    public void write(HttpServletRequest request,
                      HttpServletResponse response,
                      int status,
                      String error,
                      String message) throws IOException {
        String traceId = resolveTraceId(request);
        String path = request.getRequestURI();

        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Request-ID", traceId);

        String body = String.format(
                "{\"code\":%d,\"error\":\"%s\",\"message\":\"%s\",\"traceId\":\"%s\",\"path\":\"%s\"}",
                status, escape(error), escape(message), escape(traceId), escape(path));
        response.getWriter().write(body);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-ID");
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("traceparent");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
