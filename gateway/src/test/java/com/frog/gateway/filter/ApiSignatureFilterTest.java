package com.frog.gateway.filter;

import com.frog.gateway.properties.ApiSignatureProperties;
import com.frog.gateway.util.CachedBodyRequestDecorator;
import com.frog.gateway.util.LegacyHmacSignatureAlgorithm;
import com.frog.gateway.util.RequestSignatureCalculator;
import com.frog.gateway.util.SignatureAlgorithmRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiSignatureFilterTest {

    private ApiSignatureProperties properties;
    private SignatureAlgorithmRegistry registry;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private Set<String> nonceStore;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        properties = new ApiSignatureProperties();
        properties.setAppSecrets(Map.of("web-app", "web-secret"));
        properties.setWhitelist(List.of("/public/**"));
        properties.setDefaultVersion("HMAC-SHA256-V2");

        registry = new SignatureAlgorithmRegistry(
                java.util.List.of(new LegacyHmacSignatureAlgorithm(), new RequestSignatureCalculator()));
        registry.init();

        meterRegistry = new SimpleMeterRegistry();
        nonceStore = ConcurrentHashMap.newKeySet();
        ReactiveValueOperations<String, String> valueOps = mock(ReactiveValueOperations.class);
        redisTemplate = mock(ReactiveRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(anyString())).thenAnswer(inv -> Mono.just(nonceStore.contains(inv.getArgument(0))));
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenAnswer(inv -> {
            nonceStore.add(inv.getArgument(0));
            return Mono.just(true);
        });
    }

    @Test
    void allowsRequestWithValidSignature() {
        String appId = "web-app";
        String nonce = "nonce-1";
        String timestamp = String.valueOf(System.currentTimeMillis());

        MockServerWebExchange exchange = signedExchange("/secure/api", "{\"foo\":\"bar\"}", appId, nonce, timestamp);

        AtomicBoolean proceeded = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            proceeded.set(true);
            return Mono.empty();
        };

        new ApiSignatureFilter(redisTemplate, registry, properties, meterRegistry)
                .filter(exchange, chain)
                .block();

        assertTrue(proceeded.get(), "Filter should allow valid signature");
    }

    @Test
    void blocksReplayNonce() {
        String appId = "web-app";
        String nonce = "nonce-replay";
        String timestamp = String.valueOf(System.currentTimeMillis());

        MockServerWebExchange exchange = signedExchange("/secure/api", "{}", appId, nonce, timestamp);

        GatewayFilterChain chain = ex -> Mono.empty();
        ApiSignatureFilter filter = new ApiSignatureFilter(redisTemplate, registry, properties, meterRegistry);

        // first pass populates nonce
        filter.filter(exchange, chain).block();

        MockServerWebExchange replay = signedExchange("/secure/api", "{}", appId, nonce, timestamp);
        filter.filter(replay, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, replay.getResponse().getStatusCode());
        String body = responseBody(replay);
        assertTrue(body.contains("\"error\":\"REPLAY\""));
    }

    @Test
    void bypassesWhitelistedPaths() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/public/ping").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean proceeded = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            proceeded.set(true);
            return Mono.empty();
        };

        new ApiSignatureFilter(redisTemplate, registry, properties, meterRegistry)
                .filter(exchange, chain)
                .block();

        assertTrue(proceeded.get(), "Whitelist should bypass signature verification");
    }

    @Test
    void blocksUnknownAppId() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        MockServerHttpRequest request = MockServerHttpRequest.post("/secure/api")
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", "n-1")
                .header("X-Signature", "fake")
                .header("X-App-Id", "unknown-app")
                .header("X-Sign-Version", "HMAC-SHA256-V2")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        new ApiSignatureFilter(redisTemplate, registry, properties, meterRegistry)
                .filter(exchange, ex -> Mono.empty())
                .block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(responseBody(exchange).contains("\"error\":\"INVALID_APP_ID\""));
    }

    private MockServerWebExchange signedExchange(String path, String body, String appId, String nonce, String timestamp) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        MockServerHttpRequest unsigned = MockServerHttpRequest.post(path)
                .header("X-App-Id", appId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Sign-Version", properties.getDefaultVersion())
                .body(bodyBytes);
        ServerHttpRequest requestForSignature = new CachedBodyRequestDecorator(unsigned, bodyBytes);

        String signature = registry.getAlgorithm(properties.getDefaultVersion())
                .calculate(requestForSignature, appId, timestamp, nonce, properties.getAppSecrets().get(appId))
                .block();

        MockServerHttpRequest signed = MockServerHttpRequest.post(path)
                .header("X-App-Id", appId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Sign-Version", properties.getDefaultVersion())
                .header("X-Signature", signature)
                .body(bodyBytes);
        ServerHttpRequest decorated = new CachedBodyRequestDecorator(signed, bodyBytes);
        return MockServerWebExchange.from(decorated);
    }

    private String responseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
