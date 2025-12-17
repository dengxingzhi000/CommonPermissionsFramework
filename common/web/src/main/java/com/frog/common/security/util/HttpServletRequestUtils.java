package com.frog.common.security.util;

import com.frog.common.security.properties.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一处理 Token提取
 *
 * @author Deng
 * createData 2025/10/20 16:23
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class HttpServletRequestUtils {
    private final JwtProperties jwtProperties;

    public String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtProperties.getPrefix())) {
            return bearerToken.substring(jwtProperties.getPrefix().length());
        }
        return null;
    }

    public String getDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-ID");

        if (!StringUtils.hasText(deviceId)) {
            String userAgent = request.getHeader("User-Agent");
            String ip = IpUtils.getClientIp(request);
            String raw = (userAgent != null ? userAgent : "unknown") + "|" + ip;
            deviceId = DigestUtils.sha256Hex(raw);
        } else {
            // 清理非法字符（只保留字母、数字、横线、下划线）
            deviceId = deviceId.replaceAll("[^a-zA-Z0-9-_]", "");
        }

        return deviceId;
    }
}
