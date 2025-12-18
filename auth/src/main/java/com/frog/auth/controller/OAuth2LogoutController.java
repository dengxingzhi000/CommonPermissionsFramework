package com.frog.auth.controller;

import com.frog.common.response.ApiResponse;
import com.frog.common.security.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * OAuth2 登出控制器
 * 提供 OAuth2 授权撤销功能
 *
 * @author Deng
 * @since 2025-11-10
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Tag(
        name = "OAuth2 登出",
        description = "OAuth2 授权撤销管理"
)
public class OAuth2LogoutController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2AuthorizationService authorizationService;
    private final JwtUtils jwtUtils;

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "OAuth2 登出",
            description = "撤销 OAuth2 授权，支持单客户端撤销或全局登出"
    )
    public ApiResponse<Void> logout(
            @Parameter(description = "Bearer Token", required = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "客户端 ID（可选，不传则全局登出）")
            @RequestParam(required = false) String clientId) {

        // 验证并解析 Token
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Invalid authorization header format");
            return ApiResponse.fail(400, "Invalid authorization header");
        }

        String accessToken = authHeader.substring(BEARER_PREFIX.length());
        if (!StringUtils.hasText(accessToken)) {
            log.warn("Empty access token");
            return ApiResponse.fail(400, "Empty access token");
        }

        UUID userId = jwtUtils.getUserIdFromToken(accessToken);

        if (StringUtils.hasText(clientId)) {
            // 撤销特定客户端的授权
            OAuth2Authorization authorization =
                    authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization != null) {
                authorizationService.remove(authorization);
                log.info("OAuth2 logout: revoked authorization for userId={} clientId={}", userId, clientId);
            } else {
                log.warn("OAuth2 logout: authorization not found for userId={} clientId={}", userId, clientId);
            }
        } else {
            // 撤销所有授权(全局登出)
            jwtUtils.revokeAllUserTokens(userId);
            log.info("OAuth2 global logout: revoked all tokens for userId={}", userId);
        }

        return ApiResponse.success();
    }
}