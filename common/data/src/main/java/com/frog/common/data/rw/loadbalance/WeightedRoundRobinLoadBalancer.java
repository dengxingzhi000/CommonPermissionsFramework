package com.frog.common.data.rw.loadbalance;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加权轮询负载均衡器
 * <p>
 * 实现平滑加权轮询算法（Nginx 同款）
 *
 * @author Deng
 * @since 2025-12-16
 */
public class WeightedRoundRobinLoadBalancer extends AbstractLoadBalancer {
    /**
     * 当前权重
     */
    private final Map<String, AtomicInteger> currentWeights = new ConcurrentHashMap<>();

    @Override
    protected String doSelect(List<SlaveInfo> available) {
        // 计算总权重
        int totalWeight = available.stream()
                .mapToInt(SlaveInfo::weight)
                .sum();

        // 平滑加权轮询
        SlaveInfo selected = null;
        int maxCurrentWeight = Integer.MIN_VALUE;

        for (SlaveInfo slave : available) {
            // 初始化当前权重
            currentWeights.computeIfAbsent(slave.name(), k -> new AtomicInteger(0));

            // 增加当前权重
            int current = currentWeights.get(slave.name()).addAndGet(slave.weight());

            if (current > maxCurrentWeight) {
                maxCurrentWeight = current;
                selected = slave;
            }
        }

        if (selected != null) {
            // 选中的节点减去总权重
            currentWeights.get(selected.name()).addAndGet(-totalWeight);
            return selected.name();
        }

        return getFirstName(available);
    }
}
