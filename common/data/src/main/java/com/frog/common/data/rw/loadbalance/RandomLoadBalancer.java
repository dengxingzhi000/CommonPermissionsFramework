package com.frog.common.data.rw.loadbalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机负载均衡器
 *
 * @author Deng
 * @since 2025-12-16
 */
public class RandomLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected String doSelect(List<SlaveInfo> available) {
        int index = ThreadLocalRandom.current().nextInt(available.size());
        return available.get(index).name();
    }
}
