package com.frog.common.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frog.common.security.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Jwt 拒绝处理器
 *
 * @author Deng
 * createData 2025/10/11 13:55
 * @version 1.0
 */
@Component
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied: {} for user: {} from IP: {}",
                request.getRequestURI(),
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                IpUtils.getClientIp(request));

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", "权限不足，拒绝访问");
        result.put("error", accessDeniedException.getMessage());
        result.put("path", request.getRequestURI());
        result.put("timestamp", System.currentTimeMillis());

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
