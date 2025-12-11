package com.frog.auth.controller;

import com.frog.common.response.ApiResponse;
import com.frog.common.security.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 *
 *
 * @author Deng
 * createData 2025/11/10 10:31
 * @version 1.0
 */
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class OAuth2LogoutController {
    private final OAuth2AuthorizationService authorizationService;
    private final JwtUtils jwtUtils;

    @PostMapping("/logout")
    public ApiResponse<?> logout(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String clientId) {

        // 解析Token
        String accessToken = token.replace("Bearer ", ""); // 去掉"Bearer "
        UUID userId = jwtUtils.getUserIdFromToken(accessToken);

        // 撤销所有该用户的授权
        if (clientId != null) {
            // 撤销特定客户端的授权
            OAuth2Authorization authorization =
                    authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization != null) {
                authorizationService.remove(authorization);
            }
        } else {
            // 撤销所有授权(全局登出)
            jwtUtils.revokeAllUserTokens(userId);
        }

        return ApiResponse.success("Logout successful");
    }
}