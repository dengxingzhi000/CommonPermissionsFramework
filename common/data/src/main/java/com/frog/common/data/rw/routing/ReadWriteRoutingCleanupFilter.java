package com.frog.common.data.rw.routing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 确保每次请求结束后清理 ReadWriteRoutingContext 的 ThreadLocal，
 * 防止在线程池环境中内存泄漏。
 *
 * <p>必须注册为最高优先级 Filter，保证在其他 Filter/Interceptor 之前执行。
 *
 * @author Deng
 * @since 1.1
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReadWriteRoutingCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            ReadWriteRoutingContext.clear();
        }
    }
}
