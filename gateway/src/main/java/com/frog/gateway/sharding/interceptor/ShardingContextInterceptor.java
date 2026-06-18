package com.frog.gateway.sharding.interceptor;

import com.frog.common.web.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 安全地管理 HintManager（防止嵌套 / 泄露）
 *
 * <p><b>WARNING:</b> This is a Servlet-based interceptor and will NOT work in WebFlux/Gateway.
 * It is only effective in Servlet-based services (system-service, auth-service).
 * For Gateway, use a WebFilter-based equivalent instead.
 *
 * @author Deng
 * createData 2025/11/11 15:16
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Deprecated(since = "1.1", forRemoval = false)
public class ShardingContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        UUID userId = SecurityUtils.getCurrentUserUuid().orElse(null);
        if (userId != null) {
            HintManager.clear(); // 确保干净上下文
            HintManager hintManager = HintManager.getInstance();
            hintManager.addDatabaseShardingValue("sys_user", userId);
            hintManager.addTableShardingValue("sys_audit_log", LocalDateTime.now());
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        HintManager.clear();
    }
}

