package com.frog.common.data.rw.loadbalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机负载均衡器
 *
 * @author Deng
 * @since 2025-12-16
 */
public class WeightedRandomLoadBalancer extends AbstractLoadBalancer {

    @Override
    protected String doSelect(List<SlaveInfo> available) {
        // 计算总权重
        int totalWeight = available.stream()
                .mapToInt(SlaveInfo::weight)
                .sum();

        // 生成随机数
        int random = ThreadLocalRandom.current().nextInt(totalWeight);

        // 按权重区间选择
        int sum = 0;
        for (SlaveInfo slave : available) {
            sum += slave.weight();
            if (random < sum) {
                return slave.name();
            }
        }

        return getFirstName(available);
    }
}
