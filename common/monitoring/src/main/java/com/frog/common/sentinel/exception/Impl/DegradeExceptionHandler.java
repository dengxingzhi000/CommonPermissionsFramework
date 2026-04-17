package com.frog.common.sentinel.exception.Impl;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.frog.common.sentinel.exception.SentinelExceptionHandlerStrategy;
import com.frog.common.response.ApiResults;
import org.springframework.stereotype.Component;

/**
 *
 *
 * @author Deng
 * createData 2025/10/20 10:46
 * @version 1.0
 */
@Component
public class DegradeExceptionHandler implements SentinelExceptionHandlerStrategy {

    @Override
    public boolean supports(BlockException e) {
        return e instanceof DegradeException;
    }

    @Override
    public ApiResults<Void> handle(BlockException e) {
        return ApiResults.fail(503, "服务暂时不可用，请稍后再试");
    }
}