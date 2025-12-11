package com.frog.gateway.filter;

import com.frog.gateway.util.CachedBodyRequestDecorator;
import com.frog.gateway.util.SignatureAlgorithm;
import com.frog.gateway.util.SignatureAlgorithmRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

/**
 * API签名验证过滤器（防重放攻击）
 *
 * @author Deng
 * createData 2025/10/24 14:48
 * @version 2.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiSignatureFilter implements GlobalFilter, Ordered {

    private static final long EXPIRE_MILLIS = 300_000L;
    private static final String NONCE_KEY_PREFIX = "api:nonce:";
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SignatureAlgorithmRegistry algorithmRegistry;

    // 模拟配置中心
    private static final Map<String, String> APP_SECRETS = Map.of(
            "web-app", "web-secret-key",
            "mobile-app", "mobile-secret-key",
            "internal-service", "internal-secret"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return cacheRequestBody(exchange)
                .flatMap(cachedExchange -> doFilterInternal(cachedExchange, chain));
    }

    private Mono<ServerWebExchange> cacheRequestBody(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (request instanceof CachedBodyRequestDecorator) {
            return Mono.just(exchange);
        }
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(BUFFER_FACTORY.wrap(new byte[0]))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    ServerHttpRequest decorated = new CachedBodyRequestDecorator(request, bytes);
                    return exchange.mutate().request(decorated).build();
                });
    }

    private Mono<Void> doFilterInternal(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (isWhitelisted(request.getPath().value())) {
            return chain.filter(exchange);
        }

        var headers = request.getHeaders();
        String timestamp = headers.getFirst("X-Timestamp");
        String nonce = headers.getFirst("X-Nonce");
        String signature = headers.getFirst("X-Signature");
        String appId = headers.getFirst("X-App-Id");
        String version = headers.getFirst("X-Sign-Version");

        if (Stream.of(timestamp, nonce, signature, appId).anyMatch(StringUtils::isBlank)) {
            return unauthorized(exchange, "缺少签名参数");
        }
        if (timestamp == null) {
            return unauthorized(exchange, "时间戳为空");
        }

        long current = System.currentTimeMillis();
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return unauthorized(exchange, "无效的时间戳");
        }

        if (Math.abs(current - requestTime) > EXPIRE_MILLIS) {
            return unauthorized(exchange, "请求已过期");
        }

        String nonceKey = NONCE_KEY_PREFIX + appId + ":" + nonce;
        return redisTemplate.hasKey(nonceKey)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return unauthorized(exchange, "请求重复（可能的重放攻击）");
                    }

                    SignatureAlgorithm algorithm = algorithmRegistry.getAlgorithm(
                            StringUtils.defaultIfBlank(version, "HMAC-SHA256-V1"));
                    String secretKey = APP_SECRETS.get(appId);

                    if (secretKey == null) {
                        return unauthorized(exchange, "无效的AppId");
                    }

                    return algorithm.verify(request, signature, appId, timestamp, nonce, secretKey)
                            .flatMap(valid -> {
                                if (!valid) {
                                    log.warn("签名验证失败 AppId={}, Path={}", appId, request.getURI().getPath());
                                    return unauthorized(exchange, "签名验证失败");
                                }

                                return redisTemplate.opsForValue()
                                        .set(nonceKey, "1", Duration.ofMillis(EXPIRE_MILLIS))
                                        .then(chain.filter(exchange));
                            });
                })
                .onErrorResume(e -> {
                    log.error("签名验证异常", e);
                    return unauthorized(exchange, "签名验证异常");
                });
    }

    private boolean isWhitelisted(String path) {
        return path.startsWith("/public") || path.startsWith("/actuator");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
