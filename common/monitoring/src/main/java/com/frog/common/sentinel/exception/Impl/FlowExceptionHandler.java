package com.frog.common.sentinel.exception.Impl;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.frog.common.response.ApiResults;
import com.frog.common.sentinel.exception.SentinelExceptionHandlerStrategy;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author Deng
 * createData 2025/10/20 10:45
 * @version 1.0
 */
@Component
public class FlowExceptionHandler implements SentinelExceptionHandlerStrategy {

    @Override
    public boolean supports(BlockException e) {
        return e instanceof FlowException;
    }

    @Override
    public ApiResults<Void> handle(BlockException e) {
        return ApiResults.fail(429, "请求过于频繁，请稍后再试");
    }
}