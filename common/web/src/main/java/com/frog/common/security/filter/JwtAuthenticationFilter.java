package com.frog.common.security.filter;

import com.frog.common.security.metrics.SecurityMetrics;
import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.security.util.IpUtils;
import com.frog.common.security.util.JwtUtils;
import com.frog.common.security.util.SecurityErrorResponseWriter;
import com.frog.common.web.domain.SecurityUser;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Jwt 过滤器
 *
 * @author Deng
 * createData 2025/10/11 13:49
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final HttpServletRequestUtils httpServletRequestUtils;
    private final SecurityMetrics securityMetrics;

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 获取 Token
            String token = httpServletRequestUtils.getTokenFromRequest(request);

            if (StringUtils.hasText(token)) {
                // 获取当前请求信息
                String currentIp = IpUtils.getClientIp(request);
                String currentDeviceId = httpServletRequestUtils.getDeviceId(request);

                // 验证 Token
                if (jwtUtils.validateToken(token, currentIp, currentDeviceId)) {
                    // 提取用户信息
                    UUID userId = jwtUtils.getUserIdFromToken(token);
                    String username = jwtUtils.getUsernameFromToken(token);
                    Set<String> permissions = jwtUtils.getPermissionsFromToken(token);
                    Set<String> roles = jwtUtils.getRolesFromToken(token);

                    // 构建权限列表
                    Set<SimpleGrantedAuthority> authorities = permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toSet());

                    authorities.addAll(roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toSet()));

                    // 创建认证对象
                    SecurityUser userDetails = SecurityUser.builder()
                            .userId(userId)
                            .username(username)
                            .permissions(permissions)
                            .roles(roles)
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, authorities);

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置到 Security上下文
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("User authenticated: traceId={}, userId={}, username={}",
                            request.getHeader("X-Request-ID"), userId, username);
                } else {
                    securityMetrics.increment("security.jwt.invalid");
                    SecurityErrorResponseWriter.write(request, response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "INVALID_TOKEN",
                            "Token validation failed");
                    return;
                }
            }
        } catch (Exception e) {
            securityMetrics.increment("security.jwt.errors");
            log.error("Cannot set user authentication traceId={}", request.getHeader("X-Request-ID"), e);
            SecurityErrorResponseWriter.write(request, response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_ERROR",
                    "Authentication error");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
