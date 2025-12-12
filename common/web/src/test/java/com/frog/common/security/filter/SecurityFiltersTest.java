package com.frog.common.security.filter;

import com.frog.common.access.PermissionAccessPort;
import com.frog.common.log.service.ISysAuditLogService;
import com.frog.common.security.config.ApiAccessControlProperties;
import com.frog.common.security.config.SecurityFilterProperties;
import com.frog.common.security.metrics.SecurityMetrics;
import com.frog.common.security.stepup.StepUpEvaluator;
import com.frog.common.security.stepup.StepUpFilter;
import com.frog.common.security.stepup.StepUpRequirement;
import com.frog.common.security.stepup.StepUpProperties;
import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.security.util.JwtUtils;
import com.frog.common.security.properties.JwtProperties;
import com.frog.common.web.domain.SecurityUser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SecurityFiltersTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiAccessControlReturnsJsonOnForbidden() throws ServletException, IOException {
        PermissionAccessPort permissionAccess = Mockito.mock(PermissionAccessPort.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        ApiAccessControlProperties apiProps = new ApiAccessControlProperties();
        apiProps.setWhitelist(List.of());

        when(permissionAccess.findPermissionsByUrl(any(), any()))
                .thenReturn(List.of("perm:read"));
        when(permissionAccess.findAllPermissionsByUserId(any()))
                .thenReturn(Set.of());

        ApiAccessControlFilter filter = new ApiAccessControlFilter(permissionAccess, auditLogService, metrics, apiProps);

        SecurityUser principal = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("alice")
                .roles(Set.of())
                .permissions(Set.of())
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/data");
        request.addHeader("X-Request-ID", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Forbidden request should not proceed");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("ACCESS_DENIED"));
        assertTrue(body.contains("req-123"));
    }

    @Test
    void apiAccessControlSnapshot() throws ServletException, IOException {
        PermissionAccessPort permissionAccess = Mockito.mock(PermissionAccessPort.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        ApiAccessControlProperties apiProps = new ApiAccessControlProperties();
        apiProps.setWhitelist(List.of());

        when(permissionAccess.findPermissionsByUrl(any(), any()))
                .thenReturn(List.of("perm:read"));
        when(permissionAccess.findAllPermissionsByUserId(any()))
                .thenReturn(Set.of());

        ApiAccessControlFilter filter = new ApiAccessControlFilter(permissionAccess, auditLogService, metrics, apiProps);

        SecurityUser principal = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("alice")
                .roles(Set.of())
                .permissions(Set.of())
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/data");
        request.addHeader("X-Request-ID", "trace-api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Forbidden request should not proceed");
        assertEquals("{\"code\":403,\"error\":\"ACCESS_DENIED\",\"message\":\"您没有访问该资源的权限\",\"traceId\":\"trace-api\",\"path\":\"/secure/data\"}",
                response.getContentAsString());
    }

    @Test
    void apiAccessControlAllowsWhenPermissionMatches() throws ServletException, IOException {
        PermissionAccessPort permissionAccess = Mockito.mock(PermissionAccessPort.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        ApiAccessControlProperties apiProps = new ApiAccessControlProperties();
        apiProps.setWhitelist(List.of());

        when(permissionAccess.findPermissionsByUrl(any(), any()))
                .thenReturn(List.of("perm:read"));
        when(permissionAccess.findAllPermissionsByUserId(any()))
                .thenReturn(Set.of("perm:read"));

        ApiAccessControlFilter filter = new ApiAccessControlFilter(permissionAccess, auditLogService, metrics, apiProps);

        SecurityUser principal = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("alice")
                .roles(Set.of("role:a"))
                .permissions(Set.of("perm:read"))
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/data");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Authorized request should proceed");
        assertEquals(200, response.getStatus());
    }

    @Test
    void sqlInjectionFilterReturnsJson() throws ServletException, IOException {
        SecurityFilterProperties props = new SecurityFilterProperties();
        props.setMode(SecurityFilterProperties.SqlFilterMode.BLOCK);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        SqlInjectionFilter filter = new SqlInjectionFilter(props, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addParameter("q", "1' union select password from users --");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Malicious request should be blocked");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertTrue(response.getContentAsString().contains("SQL_INJECTION"));
    }

    @Test
    void sqlInjectionFilterSnapshotMatches() throws ServletException, IOException {
        SecurityFilterProperties props = new SecurityFilterProperties();
        props.setMode(SecurityFilterProperties.SqlFilterMode.BLOCK);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        SqlInjectionFilter filter = new SqlInjectionFilter(props, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addParameter("q", "1' union select password from users --");
        request.addHeader("X-Request-ID", "trace-sql");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Malicious request should be blocked");
        assertEquals("{\"code\":403,\"error\":\"SQL_INJECTION\",\"message\":\"检测到疑似 SQL 注入\",\"traceId\":\"trace-sql\",\"path\":\"/search\"}",
                response.getContentAsString());
    }

    @Test
    void sqlInjectionFilterAlertsWhenMonitorOnly() throws ServletException, IOException {
        SecurityFilterProperties props = new SecurityFilterProperties();
        props.setMode(SecurityFilterProperties.SqlFilterMode.ALERT);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        SqlInjectionFilter filter = new SqlInjectionFilter(props, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addParameter("q", "1' union select password from users --");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Monitor mode should not block the request");
        assertEquals("SQL_INJECTION", response.getHeader("X-Sql-Guard"));
    }

    @Test
    void jwtFilterAllowsValidToken() throws ServletException, IOException {
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());

        when(jwtUtils.validateToken(anyString(), anyString(), anyString())).thenReturn(true);
        UUID uid = UUID.randomUUID();
        when(jwtUtils.getUserIdFromToken(anyString())).thenReturn(uid);
        when(jwtUtils.getUsernameFromToken(anyString())).thenReturn("bob");
        when(jwtUtils.getPermissionsFromToken(anyString())).thenReturn(Set.of("perm:a"));
        when(jwtUtils.getRolesFromToken(anyString())).thenReturn(Set.of("role:b"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, requestUtils, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        request.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Valid token should proceed");
    }

    @Test
    void jwtFilterSnapshotOnInvalidToken() throws ServletException, IOException {
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());

        when(jwtUtils.validateToken(anyString(), anyString(), anyString())).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, requestUtils, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        request.addHeader("Authorization", "Bearer badtoken");
        request.addHeader("X-Request-ID", "trace-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Invalid token should not proceed");
        assertEquals("{\"code\":401,\"error\":\"INVALID_TOKEN\",\"message\":\"Token validation failed\",\"traceId\":\"trace-jwt\",\"path\":\"/secure\"}",
                response.getContentAsString());
    }

    @Test
    void jwtFilterBlocksInvalidToken() throws ServletException, IOException {
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());

        when(jwtUtils.validateToken(anyString(), anyString(), anyString())).thenReturn(false);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtils, requestUtils, metrics);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");
        request.addHeader("Authorization", "Bearer badtoken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Invalid token should not proceed");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_TOKEN"));
        assertNotNull(response.getHeader("X-Request-ID"));
    }

    @Test
    void stepUpFilterRequiresMfaWhenAmrMissing() throws ServletException, IOException {
        StepUpEvaluator evaluator = Mockito.mock(StepUpEvaluator.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        StepUpProperties stepUpProperties = new StepUpProperties();

        when(evaluator.evaluate(any(), any())).thenReturn(StepUpRequirement.MFA);
        when(requestUtils.getTokenFromRequest(any())).thenReturn("tok");
        when(jwtUtils.getAmrFromToken(anyString())).thenReturn(Set.of());

        StepUpFilter filter = new StepUpFilter(evaluator, auditLogService, jwtUtils, requestUtils, metrics, stepUpProperties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertFalse(proceeded.get(), "Should require step-up");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("STEP_UP_REQUIRED"));
        assertEquals("mfa", response.getHeader("X-StepUp-Required"));
    }

    @Test
    void stepUpFilterPassesWhenAmrSatisfied() throws ServletException, IOException {
        StepUpEvaluator evaluator = Mockito.mock(StepUpEvaluator.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        StepUpProperties stepUpProperties = new StepUpProperties();

        when(evaluator.evaluate(any(), any())).thenReturn(StepUpRequirement.WEBAUTHN);
        when(requestUtils.getTokenFromRequest(any())).thenReturn("tok");
        when(jwtUtils.getAmrFromToken(anyString())).thenReturn(Set.of("webauthn"));

        StepUpFilter filter = new StepUpFilter(evaluator, auditLogService, jwtUtils, requestUtils, metrics, stepUpProperties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Satisfied AMR should bypass step-up");
        assertEquals(200, response.getStatus());
    }

    @Test
    void apiAccessControlBypassesWhenCircuitOpens() throws ServletException, IOException {
        PermissionAccessPort permissionAccess = Mockito.mock(PermissionAccessPort.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        ApiAccessControlProperties apiProps = new ApiAccessControlProperties();
        apiProps.getCircuitBreaker().setFailureThreshold(1);
        apiProps.getCircuitBreaker().setBypassOnOpen(true);
        apiProps.setWhitelist(List.of());

        when(permissionAccess.findPermissionsByUrl(any(), any()))
                .thenThrow(new RuntimeException("perm service down"));

        ApiAccessControlFilter filter = new ApiAccessControlFilter(permissionAccess, auditLogService, metrics, apiProps);

        SecurityUser principal = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("alice")
                .roles(Set.of())
                .permissions(Set.of())
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/data");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Circuit open should bypass access control");
        assertEquals("api-access-circuit", response.getHeader("X-Security-Bypass"));
    }

    @Test
    void stepUpFilterCanBypassConfiguredPath() throws ServletException, IOException {
        StepUpEvaluator evaluator = Mockito.mock(StepUpEvaluator.class);
        ISysAuditLogService auditLogService = Mockito.mock(ISysAuditLogService.class);
        JwtUtils jwtUtils = Mockito.mock(JwtUtils.class);
        JwtProperties jwtProperties = new JwtProperties();
        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(jwtProperties);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        StepUpProperties stepUpProperties = new StepUpProperties();
        stepUpProperties.getBypassPaths().add("/transfer");

        when(evaluator.evaluate(any(), any())).thenReturn(StepUpRequirement.MFA);

        StepUpFilter filter = new StepUpFilter(evaluator, auditLogService, jwtUtils, requestUtils, metrics, stepUpProperties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/transfer");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean proceeded = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> proceeded.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(proceeded.get(), "Bypass path should skip step-up enforcement");
        assertEquals("config", response.getHeader("X-StepUp-Bypass"));
    }
}
