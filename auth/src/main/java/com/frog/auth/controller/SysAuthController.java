package com.frog.auth.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.frog.auth.domain.dto.WebauthnAuthenticationRequest;
import com.frog.auth.service.ISysAuthService;
import com.frog.auth.service.IWebauthnCredentialService;
import com.frog.common.dto.auth.TokenUpgradeResponse;
import com.frog.common.dto.auth.WebAuthnChallengeResponse;
import com.frog.common.dto.auth.WebAuthnRegisterChallengeResponse;
import com.frog.common.dto.auth.WebAuthnRegisterVerifyRequest;
import com.frog.common.dto.user.LoginRequest;
import com.frog.common.dto.user.LoginResponse;
import com.frog.common.dto.user.RefreshTokenRequest;
import com.frog.common.dto.user.UserInfo;
import com.frog.common.feign.client.SysUserServiceClient;
import com.frog.common.log.annotation.AuditLog;
import com.frog.common.response.ApiResponse;
import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.security.util.IpUtils;
import com.frog.common.sentinel.annotation.RateLimit;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import com.frog.system.api.UserDubboService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统认证控制器
 * 提供用户登录、登出、Token刷新、WebAuthn认证等相关接口
 *
 * @author Deng
 * @version 1.0
 * @since 2025-10-14
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class SysAuthController {
    private final ISysAuthService authService;
    private final IWebauthnCredentialService webAuthnService;
    private final SysUserServiceClient userServiceClient;
    private final HttpServletRequestUtils httpServletRequestUtils;
    private final UserDubboService userDubboService;

    /**
     * 用户登录接口
     * 
     * @param request 登录请求参数
     * @param httpRequest HTTP请求对象
     * @return 登录响应结果
     */
    @PostMapping("/login")
    @SentinelResource(value = "auth_login")
    @RateLimit()
    public ApiResponse<LoginResponse> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = IpUtils.getClientIp(httpRequest);
        String deviceId = httpServletRequestUtils.getDeviceId(httpRequest);

        LoginResponse response = authService.login(request, ipAddress, deviceId);

        return ApiResponse.success(response);
    }

    /**
     * WebAuthn 断言挑战（Step-Up 前置）
     * 
     * @param httpRequest HTTP请求对象
     * @param rpId Party ID
     * @param currentUser 当前安全用户
     * @return WebAuthn挑战响应
     */
    @GetMapping("/webauthn/challenge")
    public ApiResponse<WebAuthnChallengeResponse> webAuthnChallenge(
            HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "auth.example.com") String rpId,
            @AuthenticationPrincipal SecurityUser currentUser) {
        String deviceId = httpServletRequestUtils.getDeviceId(httpRequest);
        WebAuthnChallengeResponse options = webAuthnService.generateAuthenticationChallenge(
                currentUser.getUserId(),
                currentUser.getUsername(),
                deviceId,
                rpId);

        return ApiResponse.success(options);
    }

    /**
     * WebAuthn 断言验证并签发升级后的访问令牌（AMR 包含 webauthn）
     * 
     * @param request WebAuthn验证请求
     * @param httpRequest HTTP请求对象
     * @param currentUser 当前安全用户
     * @return 升级后的Token响应
     */
    @PostMapping("/webauthn/verify")
    public ApiResponse<TokenUpgradeResponse> webAuthnVerify(
            @RequestBody WebauthnAuthenticationRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal SecurityUser currentUser) {
        String ipAddress = IpUtils.getClientIp(httpRequest);
        String deviceId = httpServletRequestUtils.getDeviceId(httpRequest);
        TokenUpgradeResponse upgraded = webAuthnService.authenticateAndUpgradeToken(
                currentUser.getUserId(), 
                currentUser.getUsername(),
                request, 
                deviceId, 
                ipAddress);

        return ApiResponse.success(upgraded);
    }

    /**
     * WebAuthn 注册挑战
     * 
     * @param httpRequest HTTP请求对象
     * @param rpId Party ID
     * @param currentUser 当前安全用户
     * @return WebAuthn注册挑战响应
     */
    @GetMapping("/webauthn/register/challenge")
    public ApiResponse<WebAuthnRegisterChallengeResponse> webAuthnRegisterChallenge(
            HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "auth.example.com") String rpId,
            @AuthenticationPrincipal SecurityUser currentUser) {
        String deviceId = httpServletRequestUtils.getDeviceId(httpRequest);
        WebAuthnRegisterChallengeResponse options = webAuthnService.generateRegistrationChallenge(
                currentUser.getUserId(),
                currentUser.getUsername(),
                deviceId, 
                rpId);

        return ApiResponse.success(options);
    }

    /**
     * WebAuthn 注册验证（保存凭据）
     * 
     * @param request WebAuthn注册验证请求
     * @param httpRequest HTTP请求对象
     * @param currentUser 当前安全用户
     * @return 操作结果
     */
    @PostMapping("/webauthn/register/verify")
    public ApiResponse<Void> webAuthnRegisterVerify(
            @RequestBody WebAuthnRegisterVerifyRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal SecurityUser currentUser) {
        String ipAddress = IpUtils.getClientIp(httpRequest);
        String deviceId = httpServletRequestUtils.getDeviceId(httpRequest);
        //todo 修复注册验证代码
//        webAuthnService.registerVerify(
//                currentUser.getUserId(),
//                currentUser.getUsername(),
//                request,
//                deviceId,
//                ipAddress);

        return ApiResponse.success();
    }

    /**
     * 用户登出接口
     * 
     * @param request HTTP请求对象
     * @return 操作结果
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = httpServletRequestUtils.getTokenFromRequest(request);
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);

        authService.logout(token, userId, "用户主动登出");

        return ApiResponse.success();
    }

    /**
     * 刷新Token接口
     * 
     * @param request 刷新Token请求
     * @param httpRequest HTTP请求对象
     * @return 登录响应结果
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(
            @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = IpUtils.getClientIp(httpRequest);
        String deviceId = request.getDeviceId() != null ? 
                request.getDeviceId() :
                httpRequest.getHeader("X-Device-ID");

        LoginResponse response = authService.refreshToken(
                request.getRefreshToken(), 
                deviceId, 
                ipAddress);

        return ApiResponse.success(response);
    }

    /**
     * 获取当前用户信息接口
     * 
     * @return 用户信息
     */
    @GetMapping("/userinfo")
    public ApiResponse<UserInfo> getUserInfo() {
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);

        // 优先使用 Dubbo，失败回退到 Feign
        UserInfo userInfo;
        try {
            userInfo = userDubboService.getUserInfo(userId);
        } catch (Exception ex) {
            userInfo = userServiceClient.getUserInfo(userId).data();
        }

        return ApiResponse.success(userInfo);
    }

    /**
     * 强制用户登出接口
     * 
     * @param userId 用户ID
     * @param reason 登出原因
     * @return 操作结果
     */
    @PostMapping("/force-logout/{userId}")
    @PreAuthorize("hasAuthority('system:user:edit')")
    @AuditLog(
            operation = "强制下线",
            businessType = "USER",
            riskLevel = 3
    )
    public ApiResponse<Void> forceLogout(
            @PathVariable UUID userId, 
            @RequestParam String reason) {

        authService.forceLogout(userId, reason);

        return ApiResponse.success();
    }
}
