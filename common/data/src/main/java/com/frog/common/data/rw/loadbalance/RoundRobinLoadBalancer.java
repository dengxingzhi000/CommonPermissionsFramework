package com.frog.common.data.rw.loadbalance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询负载均衡器
 *
 * @author Deng
 * @since 2025-12-16
 */
public class RoundRobinLoadBalancer extends AbstractLoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    protected String doSelect(List<SlaveInfo> available) {
        int index = Math.abs(counter.getAndIncrement() % available.size());
        return available.get(index).name();
    }
}
