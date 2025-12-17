package com.frog.common.data.rw.loadbalance;

import java.util.List;

/**
 * 从库负载均衡器接口
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface SlaveLoadBalancer {

    /**
     * 选择一个从库
     *
     * @param slaves 可用从库列表
     * @return 选中的从库名称
     */
    String select(List<SlaveInfo> slaves);

    /**
     * 从库信息
     */
    record SlaveInfo(
            String name,
            int weight,
            int activeConnections,
            boolean available
    ) {}
}
