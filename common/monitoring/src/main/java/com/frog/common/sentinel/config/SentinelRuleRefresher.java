package com.frog.common.sentinel.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.frog.common.monitoring.config.MonitoringProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class SentinelRuleRefresher {
    private final MonitoringProperties properties;

    public SentinelRuleRefresher(MonitoringProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refreshRules();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        if (event.getKeys().stream().anyMatch(key -> key.startsWith("monitoring.sentinel"))) {
            refreshRules();
        }
    }

    void refreshRules() {
        MonitoringProperties.Sentinel sentinel = properties.getSentinel();
        if (!sentinel.isEnabled()) {
            log.debug("Sentinel dynamic rule refresh disabled");
            return;
        }
        List<FlowRule> flowRules = sentinel.getFlowRules().stream()
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .map(this::toFlowRule)
                .collect(Collectors.toList());
        FlowRuleManager.loadRules(flowRules);

        List<DegradeRule> degradeRules = sentinel.getDegradeRules().stream()
                .filter(rule -> rule.getResource() != null && !rule.getResource().isBlank())
                .map(this::toDegradeRule)
                .collect(Collectors.toList());
        DegradeRuleManager.loadRules(degradeRules);

        log.info("Loaded {} Sentinel flow rules and {} degrade rules", flowRules.size(), degradeRules.size());
    }

    private FlowRule toFlowRule(MonitoringProperties.FlowRuleConfig cfg) {
        FlowRule rule = new FlowRule();
        rule.setResource(cfg.getResource());
        rule.setGrade(cfg.getGrade());
        rule.setCount(cfg.getCount());
        rule.setControlBehavior(cfg.getControlBehavior());
        rule.setMaxQueueingTimeMs(cfg.getMaxQueueingTimeMs());
        rule.setStrategy(cfg.getStrategy());
        rule.setRefResource(cfg.getRefResource());
        rule.setWarmUpPeriodSec(cfg.getWarmUpPeriodSec());
        rule.setLimitApp(cfg.getLimitApp());
        return rule;
    }

    private DegradeRule toDegradeRule(MonitoringProperties.DegradeRuleConfig cfg) {
        DegradeRule rule = new DegradeRule();
        rule.setResource(cfg.getResource());
        rule.setGrade(cfg.getGrade());
        rule.setCount(cfg.getCount());
        rule.setTimeWindow(cfg.getTimeWindow());
        rule.setMinRequestAmount(cfg.getMinRequestAmount());
        rule.setSlowRatioThreshold(cfg.getSlowRatioThreshold());
        rule.setStatIntervalMs(cfg.getStatIntervalMs());
        return rule;
    }
}
