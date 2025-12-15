package com.frog.common.feign.factory;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.net.SocketTimeoutException;

/**
 * Feign 统一降级工厂基类
 *
 * @author Deng
 * createData 2025/10/20 11:03
 * @version 1.0
 */
@Slf4j
public abstract class BaseFallbackFactory<T> implements FallbackFactory<T> {

    @Override
    public T create(Throwable cause) {
        String errorMsg = switch (cause) {
            case FeignException.ServiceUnavailable se -> "服务暂时不可用";
            case FeignException.InternalServerError ie -> "服务内部错误";
            case SocketTimeoutException st -> "服务调用超时";
            default -> "服务调用异常";
        };

        log.error("Feign fallback: {}, error: {}", errorMsg, cause.getMessage());
        return createFallback(errorMsg, cause);
    }

    /**
     * 子类实现具体降级逻辑
     */
    protected abstract T createFallback(String errorMsg, Throwable cause);
}