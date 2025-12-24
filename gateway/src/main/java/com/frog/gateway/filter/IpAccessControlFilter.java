package com.frog.gateway.filter;

import com.alibaba.nacos.shaded.com.google.common.cache.Cache;
import com.alibaba.nacos.shaded.com.google.common.cache.CacheBuilder;
import com.frog.gateway.properties.IpAccessControlProperties;
import com.frog.gateway.filter.support.IpAccessDecision;
import com.frog.gateway.support.ip.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Hardened IP access control filter that validates trusted proxies, uses redis-backed ACLs and
 * responds with structured payloads.
 * 强化版IP访问控制过滤器，可验证受信任的代理，使用基于Redis的访问控制列表（ACL），并返回结构化数据。
 */
@Component
@Slf4j
public class IpAccessControlFilter implements GlobalFilter, Ordered {
    private static final String IP_WHITELIST_KEY = "security:ip:whitelist:";
    private static final String IP_BLACKLIST_KEY = "security:ip:blacklist:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final IpAccessControlProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final Cache<String, IpAccessDecision> decisionCache;

    public IpAccessControlFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                                 IpAccessControlProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clientIpResolver = new ClientIpResolver(properties);
        this.decisionCache = CacheBuilder.newBuilder()
                .maximumSize(Math.max(1000, properties.getCacheMaxSize()))
                .expireAfterWrite(resolveTtl(properties.getCacheTtl()), TimeUnit.MILLISECONDS)
                .build();
    }

    private long resolveTtl(Duration ttl) {
        long millis = ttl != null ? ttl.toMillis() : Duration.ofMinutes(1).toMillis();
        return Math.max(millis, 1000);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientIp = clientIpResolver.resolve(exchange);
        if (!StringUtils.hasText(clientIp)) {
            log.warn("Unable to resolve client IP for {}", exchange.getRequest().getURI());
            return blockRequest(exchange, "UNRESOLVED_IP", "");
        }

        IpAccessDecision cachedDecision = decisionCache.getIfPresent(clientIp);
        if (cachedDecision != null) {
            return handleDecision(cachedDecision, clientIp, exchange, chain);
        }

        return evaluateAccess(clientIp)
                .doOnNext(decision -> decisionCache.put(clientIp, decision))
                .flatMap(decision -> handleDecision(decision, clientIp, exchange, chain));
    }

    private Mono<IpAccessDecision> evaluateAccess(String clientIp) {
        return redisTemplate.hasKey(IP_BLACKLIST_KEY + clientIp)
                .flatMap(inBlacklist -> {
                    if (inBlacklist) {
                        return Mono.just(IpAccessDecision.deny("BLACKLIST"));
                    }
                    if (!properties.isWhitelistOnly()) {
                        return Mono.just(IpAccessDecision.allow());
                    }
                    return redisTemplate.hasKey(IP_WHITELIST_KEY + clientIp)
                            .map(inWhitelist -> inWhitelist
                                    ? IpAccessDecision.allow()
                                    : IpAccessDecision.deny("WHITELIST_ONLY"));
                })
                .defaultIfEmpty(properties.isWhitelistOnly()
                        ? IpAccessDecision.deny("WHITELIST_ONLY")
                        : IpAccessDecision.allow());
    }

    private Mono<Void> handleDecision(IpAccessDecision decision,
                                      String clientIp,
                                      ServerWebExchange exchange,
                                      GatewayFilterChain chain) {
        if (decision.allowed()) {
            return chain.filter(exchange);
        }
        log.warn("Blocked client IP {} by reason {}", clientIp, decision.reason());
        return blockRequest(exchange, decision.reason(), clientIp);
    }

    private Mono<Void> blockRequest(ServerWebExchange exchange, String reason, String clientIp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");

        String message = StringUtils.hasText(properties.getBlockMessage())
                ? properties.getBlockMessage()
                : "Request blocked";
        String body = toJson(Map.of(
                "code", HttpStatus.FORBIDDEN.value(),
                "message", message,
                "reason", reason,
                "ip", clientIp
        ));

        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
        ));
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
            first = false;
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
