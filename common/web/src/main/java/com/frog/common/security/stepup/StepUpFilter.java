package com.frog.common.security.stepup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.frog.common.log.service.ISysAuditLogService;
import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.security.util.JwtUtils;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StepUpFilter extends OncePerRequestFilter {

    private final StepUpEvaluator evaluator;
    private final ISysAuditLogService auditLogService;
    private final JwtUtils jwtUtils;
    private final HttpServletRequestUtils requestUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        SecurityUser user = SecurityUtils.getCurrentUser();
        StepUpRequirement requirement = evaluator.evaluate(request, user);
        if (requirement != StepUpRequirement.NONE) {
            String token = requestUtils.getTokenFromRequest(request);
            java.util.Set<String> amr = token != null ? jwtUtils.getAmrFromToken(token) : Collections.emptySet();
            if ((requirement == StepUpRequirement.MFA && amr.contains("mfa"))
                    || (requirement == StepUpRequirement.WEBAUTHN && amr.contains("webauthn"))) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        if (requirement != StepUpRequirement.NONE) {
            // 暂以 401 + 指示头 告知客户端需要 Step-Up
            String require = requirement == StepUpRequirement.MFA ? "mfa" : "webauthn";
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-StepUp-Required", require);

            Map<String, Object> body = new HashMap<>();
            body.put("error", "step_up_required");
            body.put("require", require);
            body.put("action", request.getMethod() + " " + request.getRequestURI());

            response.getWriter().write(objectMapper.writeValueAsString(body));

            // 审计
            if (user != null) {
                UUID userId = user.getUserId();
                auditLogService.recordSecurityEvent(
                        "STEP_UP_REQUIRED", 2, // 中风险等级
                        userId,
                        user.getUsername(),
                        request.getRemoteAddr(),
                        request.getRequestURI(),
                        false,
                        "Step-up required: " + require
                );
            }
            log.info("Step-up required: {} {} -> {}", request.getMethod(), request.getRequestURI(), require);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
