package com.frog.gateway.support.ip;

import com.frog.gateway.properties.IpAccessControlProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves the canonical client IP based on trusted proxy configuration.
 * 根据可信代理配置解析出规范的客户端 IP 地址。
 */
public final class ClientIpResolver {
    private final List<IpSubnet> trustedProxies;
    private final List<String> forwardedHeaders;

    public ClientIpResolver(IpAccessControlProperties properties) {
        this.forwardedHeaders = properties.getForwardedHeaders();
        this.trustedProxies = properties.getTrustedProxies()
                .stream()
                .map(IpSubnet::parse)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    public String resolve(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        InetAddress remoteInetAddress = remoteAddress != null ? remoteAddress.getAddress() : null;
        boolean remoteTrusted = isTrusted(remoteInetAddress);

        if (remoteTrusted && !CollectionUtils.isEmpty(forwardedHeaders)) {
            for (String headerName : forwardedHeaders) {
                String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);
                String candidate = extractClientIp(headerValue);
                if (StringUtils.hasText(candidate)) {
                    return candidate;
                }
            }
        }

        return remoteInetAddress != null ? remoteInetAddress.getHostAddress() : "";
    }

    private boolean isTrusted(InetAddress address) {
        if (address == null || trustedProxies.isEmpty()) {
            return false;
        }
        return trustedProxies.stream().anyMatch(subnet -> subnet.matches(address));
    }

    private String extractClientIp(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String[] segments = headerValue.split(",");
        return segments.length > 0 ? segments[0].trim() : null;
    }
}
