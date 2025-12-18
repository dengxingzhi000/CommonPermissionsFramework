package com.frog.common.security.filter;

import com.frog.common.security.PermissionService;
import com.frog.common.log.enums.SecurityEventType;
import com.frog.common.log.service.ISysAuditLogService;
import com.frog.common.security.config.ApiAccessControlProperties;
import com.frog.common.security.metrics.SecurityMetrics;
import com.frog.common.security.util.FilterBypassHelper;
import com.frog.common.security.util.IpUtils;
import com.frog.common.security.util.SecurityErrorResponseWriter;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API访问控制过滤器：URL/Method 精细化权限校验，支持白名单、旁路和 Sentinel 熔断。
 *
 * <p>REFACTORED: Now depends on PermissionService interface (common/core)
 * instead of PermissionAccessPort. This decouples from business modules.
 *
 * <p>使用 Sentinel 进行熔断降级保护，替代原有的 SimpleCircuitBreaker
 *
 * <p>Sentinel Resource: "api-access-control"
 *
 * @author Deng
 * @version 2.0 - Refactored to use PermissionService
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiAccessControlFilter extends OncePerRequestFilter {
    private final PermissionService permissionService;
    private final ISysAuditLogService auditLogService;
    private final SecurityMetrics securityMetrics;
    private final ApiAccessControlProperties properties;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        if (isWhitelisted(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityUser principal = SecurityUtils.getCurrentUser();
        UUID userId = principal != null ? principal.getUserId() : SecurityUtils.getCurrentUserUuid().orElse(null);
        if (userId == null) {
            // 未登录，由 JwtAuthenticationFilter 处理
            filterChain.doFilter(request, response);
            return;
        }

        if (shouldBypass(requestUri, principal)) {
            String reason = "api-access-bypass";
            markBypass(response, reason);
            securityMetrics.increment("security.access.bypass.config");
            filterChain.doFilter(request, response);
            return;
        }

        // Permission lookup is protected by Sentinel in FeignPermissionAccess
        // If circuit is open, exception will be thrown and handled by SecurityRestExceptionHandler
        List<String> requiredPermissions = permissionService.findPermissionsByUrl(requestUri, method);

        if (requiredPermissions.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Set<String> userPermissions = permissionService.findAllPermissionsByUserId(userId);

        boolean hasPermission = requiredPermissions.stream()
                .anyMatch(userPermissions::contains);

        if (!hasPermission) {
            String username = SecurityUtils.getCurrentUsername().orElse(null);
            String ipAddress = IpUtils.getClientIp(request);

            auditLogService.recordSecurityEvent(
                    SecurityEventType.UNAUTHORIZED_ACCESS.name(),
                    SecurityEventType.UNAUTHORIZED_ACCESS.getRiskLevel(),
                    userId,
                    username,
                    ipAddress,
                    requestUri,
                    false,
                    "尝试访问无权限的API: " + method + " " + requestUri
            );

            String traceId = request.getHeader("X-Request-ID");
            log.warn("Unauthorized API access: traceId={}, userId={}, user={}, uri={}, method={}, required={}",
                    traceId, userId, username, requestUri, method, requiredPermissions);

            securityMetrics.increment("security.access.denied");
            SecurityErrorResponseWriter.write(request, response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "ACCESS_DENIED",
                    "您没有访问该资源的权限");
            return;
        }

        if (isSensitiveOperation(method, requestUri)) {
            logSensitiveOperation(userId, method, requestUri);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String uri) {
        return FilterBypassHelper.matchesAny(uri, properties.getWhitelist());
    }

    private boolean shouldBypass(String uri, SecurityUser principal) {
        return FilterBypassHelper.shouldBypass(uri, principal,
                properties.getBypassPaths(),
                properties.getBypassUsers(),
                properties.getBypassRoles(),
                properties.getBypassPermissions());
    }

    private boolean isSensitiveOperation(String method, String uri) {
        if ("DELETE".equals(method)) {
            return true;
        }

        String[] sensitiveKeywords = {
                "delete", "reset", "password", "grant", "revoke",
                "approve", "reject", "lock", "unlock"
        };

        String lowerUri = uri.toLowerCase();
        for (String keyword : sensitiveKeywords) {
            if (lowerUri.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private void logSensitiveOperation(UUID userId, String method, String uri) {
        String username = SecurityUtils.getCurrentUsername().orElse(null);
        log.info("Sensitive operation: user={}, method={}, uri={}",
                username, method, uri);
        // TODO: 可以发送实时告警
    }

    private void markBypass(HttpServletResponse response, String reason) {
        response.setHeader("X-Security-Bypass", reason);
    }
}

