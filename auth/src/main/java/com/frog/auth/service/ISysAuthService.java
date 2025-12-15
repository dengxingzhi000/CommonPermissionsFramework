package com.frog.auth.service;

import com.frog.common.dto.user.LoginRequest;
import com.frog.common.dto.user.LoginResponse;

import java.util.UUID;

/**
 * 系统认证服务接口
 * 
 * <p>提供核心的认证功能，包括用户登录、登出、令牌刷新等操作</p>
 * 
 * @author system
 * @since 2025-11-27
 */
public interface ISysAuthService {

    /**
     * 用户登录
     * 
     * @param request 登录请求参数，包含用户名和密码等信息
     * @param ipAddress 客户端 IP地址
     * @param deviceId 设备 ID
     * @return 登录响应，包含访问令牌和刷新令牌
     */
    LoginResponse login(LoginRequest request, String ipAddress, String deviceId);

    /**
     * 用户登出
     * 
     * @param token 访问令牌
     * @param userId 用户 ID
     * @param reason 登出原因
     */
    void logout(String token, UUID userId, String reason);

    /**
     * 刷新访问令牌
     * 
     * @param refreshToken 刷新令牌
     * @param deviceId 设备 ID
     * @param ipAddress 客户端 IP地址
     * @return 新的登录响应，包含新的访问令牌和刷新令牌
     */
    LoginResponse refreshToken(String refreshToken, String deviceId, String ipAddress);

    /**
     * 强制用户登出
     * 
     * @param userId 用户 ID
     * @param reason 强制登出的原因
     */
    void forceLogout(UUID userId, String reason);
}
