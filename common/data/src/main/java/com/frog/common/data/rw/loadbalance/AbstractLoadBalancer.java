package com.frog.common.data.rw.loadbalance;

import java.util.List;

/**
 * 负载均衡器抽象基类
 * <p>
 * 提供公共的空值检查和可用节点过滤逻辑
 *
 * @author Deng
 * @since 2025-12-16
 */
public abstract class AbstractLoadBalancer implements SlaveLoadBalancer {

    @Override
    public final String select(List<SlaveInfo> slaves) {
        if (slaves == null || slaves.isEmpty()) {
            return null;
        }

        List<SlaveInfo> available = slaves.stream()
                .filter(SlaveInfo::available)
                .toList();

        if (available.isEmpty()) {
            return null;
        }

        return doSelect(available);
    }

    /**
     * 从可用节点中选择一个
     *
     * @param available 可用节点列表（非空）
     * @return 选中的节点名称
     */
    protected abstract String doSelect(List<SlaveInfo> available);

    /**
     * 获取第一个节点（兜底）
     */
    protected String getFirstName(List<SlaveInfo> available) {
        return available.getFirst().name();
    }
}
