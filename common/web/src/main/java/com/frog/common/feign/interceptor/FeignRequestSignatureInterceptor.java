package com.frog.common.feign.interceptor;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HmacAlgorithm;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Feign请求拦截器 - 自动添加签名
 *
 * @author Deng
 * createData 2025/10/24 14:56
 * @version 1.0
 */
@Component
@Slf4j
public class FeignRequestSignatureInterceptor implements RequestInterceptor {
    @Value("${security.feign.app-id:internal-service}")
    private String appId;
    @Value("${security.feign.secret-key:your-internal-secret-key}")
    private String secretKey;

    @Override
    public void apply(RequestTemplate template) {
        // 1. 生成时间戳和nonce
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");

        // 2. 计算签名
        String signature = calculateSignature(template, timestamp, nonce);

        // 3. 添加签名Header
        template.header("X-Timestamp", timestamp);
        template.header("X-Nonce", nonce);
        template.header("X-Signature", signature);
        template.header("X-App-Id", appId);

        log.debug("Feign request signed: {} {}", template.method(), template.url());
    }

    private String calculateSignature(RequestTemplate template, String timestamp, String nonce) {
        // 获取 URI
        String uri = template.path();

        // 获取查询参数并排序
        Map<String, String> queries = template.queries().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().isEmpty() ? "" : e.getValue().iterator().next()
                ));

        String sortedParams = sortAndConcatParams(queries);

        // 构建待签名字符串
        String signContent = timestamp + nonce + appId + uri + sortedParams;

        // HMAC-SHA256签名
        return SecureUtil.hmac(HmacAlgorithm.HmacSHA256, secretKey).digestHex(signContent);
    }

    /**
     * 对参数进行排序并拼接成字符串
     * @param params 参数映射
     * @return 排序并拼接后的参数字符串
     */
    private String sortAndConcatParams(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }
}

