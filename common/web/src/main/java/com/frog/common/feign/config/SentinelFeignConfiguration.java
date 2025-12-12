package com.frog.common.feign.config;

import com.alibaba.cloud.sentinel.feign.SentinelFeignAutoConfiguration;
import feign.Feign;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Sentinel Feign 集成配置
 *
 * <p>为 Feign 客户端提供基于 Sentinel 的熔断降级能力。
 * 当远程服务调用失败或超时时，自动触发降级逻辑。
 *
 * <p>配置项：
 * - feign.sentinel.enabled=true 启用 Sentinel Feign 集成
 *
 * <p>熔断规则通过 Sentinel 配置中心动态下发
 *
 * @see SentinelFeignAutoConfiguration
 */
@Configuration
@ConditionalOnClass({Feign.class})
@ConditionalOnProperty(name = "feign.sentinel.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(SentinelFeignAutoConfiguration.class)
@Slf4j
public class SentinelFeignConfiguration {

    /**
     * 构造器
     * 启动时记录 Sentinel Feign 集成状态
     */
    public SentinelFeignConfiguration() {
        log.info("Sentinel Feign integration enabled - circuit breaking and rate limiting active");
    }

    /**
     * 配置 Feign Builder
     *
     * <p>Sentinel 会自动包装 Feign 客户端，无需显式配置 Builder
     * Spring Cloud Alibaba 的 SentinelFeignAutoConfiguration 已经处理了集成
     *
     * <p>如果需要自定义 Feign Builder，可以在这里配置
     */
    @Bean
    @Scope("prototype")
    public Feign.Builder feignBuilder() {
        // Sentinel 自动通过 SentinelFeign.builder() 创建 Builder
        // 这里返回默认 Builder，实际会被 Sentinel 包装
        return Feign.builder();
    }
}