package com.frog.common.security.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Security-focused REST handler to provide trace-aware JSON errors without colliding with core handler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityRestExceptionHandler {

    /**
     * Pattern for validating trace IDs - only allows alphanumeric, hyphens, and underscores.
     * This prevents XSS and header injection attacks via malicious trace ID headers.
     */
    private static final Pattern VALID_TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,128}$");

    /**
     * Maximum length for sanitized messages to prevent log flooding and response bloat.
     */
    private static final int MAX_MESSAGE_LENGTH = 500;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> sanitizeMessage(err.getField()) + " " + sanitizeMessage(err.getDefaultMessage()))
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
                                                                  HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", sanitizeMessage(ex.getMessage()), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex,
                                                             HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", sanitizeMessage(ex.getMessage()), request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status,
                                                              String error,
                                                              String sanitizedMessage,
                                                              HttpServletRequest request) {
        String traceId = sanitizeTraceId(request.getHeader("X-Request-ID"));
        if (traceId == null) {
            traceId = sanitizeTraceId(request.getHeader("traceparent"));
        }
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        // Build response body with sanitized values only
        Map<String, Object> body = buildSafeResponseBody(
                status.value(),
                error,
                sanitizedMessage,
                traceId,
                request.getRequestURI()
        );

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-ID", traceId)
                .body(body);
    }

    /**
     * Builds the response body with all values properly sanitized.
     * This method ensures no user-controlled input reaches the response without sanitization.
     */
    private static Map<String, Object> buildSafeResponseBody(int code, String error, String message,
                                                             String traceId, String rawPath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", error);
        body.put("message", message);
        body.put("traceId", traceId);
        body.put("path", encodePath(rawPath));
        return body;
    }

    /**
     * URL-encodes the path to neutralize any potentially dangerous characters.
     * This prevents XSS by ensuring special characters are percent-encoded.
     */
    private static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        // URL encode to neutralize any dangerous characters like < > " '
        return URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    /**
     * Validates and sanitizes trace ID from request headers.
     * Only allows alphanumeric characters, hyphens, and underscores to prevent XSS and header injection.
     *
     * @param traceId the raw trace ID from request header
     * @return sanitized trace ID or null if invalid/empty
     */
    private String sanitizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        // Validate against allowed pattern - reject if contains potentially malicious characters
        if (VALID_TRACE_ID_PATTERN.matcher(traceId).matches()) {
            return traceId;
        }
        // Invalid trace ID format - generate a new one instead of using potentially malicious input
        return null;
    }

    /**
     * Sanitizes error messages to prevent XSS.
     * Escapes HTML entities and truncates to maximum length.
     *
     * @param message the raw error message
     * @return sanitized message safe for inclusion in response
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return "An error occurred";
        }
        // Escape HTML entities to prevent XSS
        String sanitized = HtmlUtils.htmlEscape(message);
        // Truncate to prevent response bloat
        if (sanitized.length() > MAX_MESSAGE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }
        return sanitized;
    }
}
