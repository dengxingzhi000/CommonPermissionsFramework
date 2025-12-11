package com.frog.common.security.filter;

import com.frog.common.access.PermissionAccessPort;
import com.frog.common.log.enums.SecurityEventType;
import com.frog.common.log.service.ISysAuditLogService;
import com.frog.common.security.util.IpUtils;
import com.frog.common.web.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
/**
 * API访问控制过滤器
 * 基于URL和HTTP方法进行细粒度权限控制
 *
 * @author Deng
 * createData 2025/11/6 15:24
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiAccessControlFilter extends OncePerRequestFilter {

    private final PermissionAccessPort permissionAccess;
    private final ISysAuditLogService auditLogService;

    // 白名单路径（不需要权限检查）
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/swagger-ui",
            "/v3/api-docs",
            "/doc.html",
            "/actuator/health"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        // 白名单检查
        if (isWhitelisted(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取当前用户
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);
        if (userId == null) {
            // 未登录，由JwtAuthenticationFilter处理
            filterChain.doFilter(request, response);
            return;
        }

        // 查询该API需要的权限
        List<String> requiredPermissions = permissionAccess
                .findPermissionsByUrl(requestUri, method);

        if (requiredPermissions.isEmpty()) {
            // 没有配置权限要求，放行
            filterChain.doFilter(request, response);
            return;
        }

        // 获取用户权限
        Set<String> userPermissions = permissionAccess
                .findAllPermissionsByUserId(userId);

        // 检查用户是否拥有所需权限
        boolean hasPermission = requiredPermissions.stream()
                .anyMatch(userPermissions::contains);

        if (!hasPermission) {
            // 记录未授权访问
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

            log.warn("Unauthorized API access: user={}, uri={}, method={}, required={}",
                    username, requestUri, method, requiredPermissions);

            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "您没有访问该资源的权限");
            return;
        }

        // 记录敏感操作
        if (isSensitiveOperation(method, requestUri)) {
            logSensitiveOperation(userId, method, requestUri);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查是否在白名单中
     */
    private boolean isWhitelisted(String uri) {
        return WHITE_LIST.stream().anyMatch(uri::startsWith);
    }

    /**
     * 判断是否为敏感操作
     */
    private boolean isSensitiveOperation(String method, String uri) {
        // DELETE操作
        if ("DELETE".equals(method)) {
            return true;
        }

        // 包含敏感关键词的URI
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

    /**
     * 记录敏感操作
     */
    private void logSensitiveOperation(UUID userId, String method, String uri) {
        String username = SecurityUtils.getCurrentUsername().orElse(null);
        log.info("Sensitive operation: user={}, method={}, uri={}",
                username, method, uri);

        // TODO: 可以发送实时告警
    }
}
