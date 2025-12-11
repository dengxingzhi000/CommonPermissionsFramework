package com.frog.common.security.filter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.frog.common.response.ApiResponse;
import com.frog.common.security.config.SecurityFilterProperties;
import com.frog.common.security.util.IpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.regex.Pattern;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
/**
 *
 *
 * @author Deng
 * createData 2025/10/24 16:11
 * @version 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SqlInjectionFilter implements Filter {

    private final SecurityFilterProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // SQL 注入模式
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        """
        (/\\*(?:.|[\\n\\r])*?\\*/)|(?:--[\\s\\S]*?$)|
        (\\bunion\\s+(?:all\\s+)?select\\b)|
        ((?:['"]|\\))\\s*(?:or|and)\\s*(?:true|false|\\d+|[a-zA-Z_][a-zA-Z0-9_]*\\s*=\\s*[a-zA-Z0-9_]+))|
        \\b(drop|truncate|exec|execute|declare)\\b
        """.replaceAll("\\n", ""),
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );


    // XSS 简易检测（建议依赖 CSP/输出编码，此处仅作兜底，可关闭）
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "<script[^>]*?>.*?</script>|javascript:|<iframe.*?>|<img[^>]*?onerror\\s*=",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 配置开关：未启用则放行
        if (properties != null && !properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        // 路径排除：命中排除的模式则放行
        String uri = httpRequest.getRequestURI();
        if (properties != null) {
            List<String> excludes = properties.getExcludePaths();
            if (excludes != null) {
                for (String pattern : excludes) {
                    if (StrUtil.isNotBlank(pattern) && pathMatcher.match(pattern.trim(), uri)) {
                        chain.doFilter(request, response);
                        return;
                    }
                }
            }
        }

        // 跳过 multipart（文件上传等）
        String contentType = httpRequest.getContentType();
        if (StrUtil.isNotBlank(contentType) && StrUtil.startWithIgnoreCase(contentType, "multipart/")) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = httpRequest;

        // JSON 体扫描（仅限小体积且方法可能包含请求体）
        if (StrUtil.isNotBlank(contentType)
                && StrUtil.startWithIgnoreCase(contentType, "application/json")
                && StrUtil.equalsAnyIgnoreCase(httpRequest.getMethod(), "POST", "PUT", "PATCH")) {
            int maxScanBytes = 64 * 1024; // 64KB 上限
            int contentLength = httpRequest.getContentLength();
            if (contentLength < 0 || contentLength <= maxScanBytes) {
                byte[] bodyBytes = httpRequest.getInputStream().readAllBytes();
                if (bodyBytes.length <= maxScanBytes) {
                    String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                    if (StrUtil.isNotBlank(bodyStr)) {
                        try {
                            Object json = com.alibaba.fastjson2.JSON.parse(bodyStr);
                            if (containsMaliciousInJson(json, httpRequest.getRequestURI(), httpRequest)) {
                                sendErrorResponse(httpResponse, "检测到疑似 JSON 体注入");
                                return;
                            }
                        } catch (Exception ignored) {
                            // 非严格 JSON 或解析失败，跳过 JSON 体扫描
                        }
                    }
                }
                // 用缓存包装器替换请求，以允许后续链路读取请求体
                requestToUse = new CachedBodyHttpServletRequest(httpRequest, bodyBytes);
            }
        }

        for (String paramName : httpRequest.getParameterMap().keySet()) {
            String[] paramValues = httpRequest.getParameterValues(paramName);
            if (paramValues == null) continue;

            for (String paramValue : paramValues) {
                if (StrUtil.isNotBlank(paramValue)) {
                    // SQL 注入检测
                    if (SQL_INJECTION_PATTERN.matcher(paramValue).find()) {
                        String preview = generatePreview(paramValue);
                        log.warn("SQL injection detected! Param: {}, Preview: {}, IP: {}, URI: {}",
                                paramName, preview, IpUtils.getClientIp(httpRequest), uri);
                        sendErrorResponse(httpResponse, "检测到疑似 SQL 注入");
                        return;
                    }

                    // XSS 检测（可配置）
                    if (properties == null || properties.isXssEnabled()) {
                        if (XSS_PATTERN.matcher(paramValue).find()) {
                            String preview = generatePreview(paramValue);
                            log.warn("XSS attack detected! Param: {}, Preview: {}, IP: {}, URI: {}",
                                    paramName, preview, IpUtils.getClientIp(httpRequest), uri);
                            sendErrorResponse(httpResponse, "检测到疑似 XSS 攻击");
                            return;
                        }
                    }
                }
            }
        }

        chain.doFilter(requestToUse, response);
    }

    private boolean containsMaliciousInJson(Object node, String uri, HttpServletRequest req) {
        if (node == null) return false;

        switch (node) {
            case JSONObject obj -> {
                for (String key : obj.keySet()) {
                    Object val = obj.get(key);
                    if (val instanceof CharSequence cs) {
                        String s = cs.toString();
                        if (isMalicious(s, key, uri, req)) return true;
                    } else {
                        if (containsMaliciousInJson(val, uri, req)) return true;
                    }
                }
                return false;
            }
            case JSONArray arr -> {
                for (Object val : arr) {
                    if (containsMaliciousInJson(val, uri, req)) return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean isMalicious(String value, String field, String uri, HttpServletRequest req) {
        if (StrUtil.isBlank(value)) return false;

        // SQL 注入检测
        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            String preview = generatePreview(value);
            log.warn("SQL injection detected in JSON! Field: {}, Preview: {}, IP: {}, URI: {}",
                    field, preview, IpUtils.getClientIp(req), uri);
            return true;
        }

        // XSS 检测（可配置）
        if ((properties == null || properties.isXssEnabled()) && XSS_PATTERN.matcher(value).find()) {
            String preview = generatePreview(value);
            log.warn("XSS attack detected in JSON! Field: {}, Preview: {}, IP: {}, URI: {}",
                    field, preview, IpUtils.getClientIp(req), uri);
            return true;
        }

        return false;
    }

    private String generatePreview(String value) {
        return value.length() > 64 ? (value.substring(0, 64) + "..." + "(len=" + value.length() + ")") : value;
    }


    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> result = ApiResponse.fail(403, message);
        response.getWriter().write(JSON.toJSONString(result));
    }
}
