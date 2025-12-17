package com.frog.common.sentinel.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.frog.common.response.ApiResponse;

/**
 * Sentinel 异常处理器策略接口
 *
 * @author Deng
 * createData 2025/10/20 10:39
 * @version 1.0
 */
public interface SentinelExceptionHandlerStrategy {
    /**
     * 判断是否支持处理该异常
     * @param ex 异常
     * @return 是否支持
     */
    boolean supports(BlockException ex);
    
    /**
     * 处理异常
     * @param ex 异常
     * @return 响应结果
     */
    ApiResponse<Void> handle(BlockException ex);
}