package com.frog.gateway.util;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * 签名算法接口
 * <p>
 * 定义了API请求签名的计算和验证方法，用于确保请求的完整性和来源合法性。
 * 支持响应式编程模型，适用于Spring WebFlux环境。
 * </p>
 *
 * @author Deng
 * @version 1.0
 * @since 2025/11/11 9:19
 */
public interface SignatureAlgorithm {

    /**
     * 获取签名算法版本号
     *
     * @return 算法版本字符串
     */
    String version();

    /**
     * 计算请求签名
     *
     * @param request   HTTP 请求对象
     * @param appId     应用标识
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @return 计算得出的签名字符串
     */
    Mono<String> calculate(ServerHttpRequest request, String appId, String timestamp, String nonce, String secretKey);

    /**
     * 验证请求签名
     *
     * @param request   HTTP 请求对象
     * @param signature 待验证的签名
     * @param appId     应用标识
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param secretKey 密钥
     * @return 签名验证结果，true表示验证通过，false表示验证失败
     */
    Mono<Boolean> verify(ServerHttpRequest request, String signature, String appId, String timestamp,
                         String nonce, String secretKey);
}
