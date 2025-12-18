package com.frog.gateway.util;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HmacAlgorithm;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base class for HMAC-SHA256 signature algorithms.
 * Provides common implementation for signature calculation and verification.
 *
 * @author Deng
 * @version 1.0
 */
public abstract class AbstractHmacSignatureAlgorithm implements SignatureAlgorithm {
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Override
    public Mono<String> calculate(ServerHttpRequest request, String appId, String timestamp,
                                  String nonce, String secretKey) {
        return canonicalPayload(request, appId, timestamp, nonce)
                .map(payload -> SecureUtil.hmac(HmacAlgorithm.HmacSHA256, secretKey).digestHex(payload));
    }

    @Override
    public Mono<Boolean> verify(ServerHttpRequest request, String signature, String appId,
                                String timestamp, String nonce, String secretKey) {
        byte[] provided = signature.getBytes(StandardCharsets.UTF_8);
        return canonicalPayload(request, appId, timestamp, nonce)
                .map(payload -> SecureUtil.hmac(HmacAlgorithm.HmacSHA256, secretKey).digestHex(payload))
                .map(calculated -> MessageDigest.isEqual(provided, calculated.getBytes(StandardCharsets.UTF_8)));
    }

    private Mono<String> canonicalPayload(ServerHttpRequest request, String appId, String timestamp, String nonce) {
        return extractBodyBytes(request)
                .map(bodyBytes -> buildCanonicalRequest(request, appId, timestamp, nonce, bodyBytes));
    }

    private Mono<byte[]> extractBodyBytes(ServerHttpRequest request) {
        if (request instanceof CachedBodyRequestDecorator cached) {
            return Mono.just(cached.getCachedBody());
        }
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(BUFFER_FACTORY.wrap(new byte[0]))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                });
    }

    private String buildCanonicalRequest(ServerHttpRequest request, String appId, String timestamp,
                                         String nonce, byte[] bodyBytes) {
        String bodyHash = DigestUtils.sha256Hex(bodyBytes);
        String query = canonicalizeQuery(request.getQueryParams());
        return String.format("""
                            ts=%s
                            nonce=%s
                            appId=%s
                            path=%s
                            query=%s
                            bodyHash=%s""", timestamp, nonce, appId, request.getURI().getRawPath(), query, bodyHash);
    }

    private String canonicalizeQuery(MultiValueMap<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        return queryParams.entrySet().stream()
                .flatMap(entry -> toEntries(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Map.Entry<String, String>::getKey)
                        .thenComparing(Map.Entry::getValue))
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private Stream<Map.Entry<String, String>> toEntries(String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return Stream.of(Map.entry(key, ""));
        }
        return values.stream().map(value -> Map.entry(key, value));
    }

    private String percentEncode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}