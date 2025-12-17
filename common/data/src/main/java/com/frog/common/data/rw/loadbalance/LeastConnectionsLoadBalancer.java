package com.frog.common.data.rw.loadbalance;

import java.util.Comparator;
import java.util.List;

/**
 * 最少连接负载均衡器
 *
 * @author Deng
 * @since 2025-12-16
 */
public class LeastConnectionsLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected String doSelect(List<SlaveInfo> available) {
        return available.stream()
                .min(Comparator.comparingInt(SlaveInfo::activeConnections))
                .map(SlaveInfo::name)
                .orElseGet(() -> getFirstName(available));
    }
}
