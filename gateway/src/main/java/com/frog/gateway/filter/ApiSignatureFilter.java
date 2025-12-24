package com.frog.gateway.filter;

import com.frog.gateway.properties.ApiSignatureProperties;
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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Stream;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * API signature validation filter with configurable replay protection.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiSignatureFilter implements GlobalFilter, Ordered {
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SignatureAlgorithmRegistry algorithmRegistry;
    private final ApiSignatureProperties properties;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        meterRegistry.counter("gateway.signature.requests").increment();
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }
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
            return unauthorized(exchange, "MISSING_PARAMETERS", "Missing signature parameters");
        }

        assert timestamp != null && nonce != null && signature != null && appId != null : "Parameters already validated";

        long current = System.currentTimeMillis();
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return unauthorized(exchange, "INVALID_TIMESTAMP", "Invalid timestamp");
        }

        long skew = properties.getAllowedClockSkew().toMillis();
        if (skew < 1000) {
            skew = Duration.ofMinutes(1).toMillis();
        }
        if (Math.abs(current - requestTime) > skew) {
            return unauthorized(exchange, "REQUEST_EXPIRED", "Request expired");
        }

        String nonceKey = properties.getNonceKeyPrefix() + appId + ":" + nonce;
        return redisTemplate.hasKey(nonceKey)
                .flatMap(exists -> {
                    if (exists) {
                        meterRegistry.counter("gateway.signature.replay").increment();
                        log.warn("Signature replay detected traceId={} appId={} path={}",
                                exchange.getRequest().getId(), appId, request.getURI().getPath());
                        return unauthorized(exchange, "REPLAY", "Replay detected");
                    }

                    SignatureAlgorithm algorithm = algorithmRegistry.getAlgorithm(
                            StringUtils.defaultIfBlank(version, properties.getDefaultVersion()));
                    if (algorithm == null) {
                        return unauthorized(exchange, "UNSUPPORTED_VERSION", "Unsupported signature version");
                    }
                    String secretKey = properties.getAppSecrets().get(appId);

                    if (secretKey == null) {
                        meterRegistry.counter("gateway.signature.invalid_app").increment();
                        log.warn("Unknown appId signature traceId={} appId={} path={}",
                                exchange.getRequest().getId(), appId, request.getURI().getPath());
                        return unauthorized(exchange, "INVALID_APP_ID", "Invalid appId");
                    }

                    return algorithm.verify(request, signature, appId, timestamp, nonce, secretKey)
                            .flatMap(valid -> {
                                if (!valid) {
                                    meterRegistry.counter("gateway.signature.invalid").increment();
                                    log.warn("Signature verification failed traceId={} appId={} path={}",
                                            exchange.getRequest().getId(), appId, request.getURI().getPath());
                                    return unauthorized(exchange, "SIGNATURE_INVALID", "Signature verification failed");
                                }

                                return redisTemplate.opsForValue()
                                        .set(nonceKey, "1", properties.getNonceTtl())
                                        .then(chain.filter(exchange));
                            });
                })
                .onErrorResume(e -> {
                    meterRegistry.counter("gateway.signature.errors").increment();
                    log.error("Signature validation error traceId={}", exchange.getRequest().getId(), e);
                    return unauthorized(exchange, "INTERNAL_ERROR", "Signature validation error");
                });
    }

    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String code, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        byte[] bytes = buildErrorBody(code, msg, exchange).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory().wrap(bytes)));
    }

    private String buildErrorBody(String code, String message, ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return String.format("{\"code\":401,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                code, escape(message), escape(path));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
