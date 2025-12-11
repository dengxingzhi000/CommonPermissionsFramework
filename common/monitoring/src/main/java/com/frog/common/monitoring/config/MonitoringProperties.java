package com.frog.common.monitoring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "monitoring")
public class MonitoringProperties {
    /**
     * Logical service name used for telemetry resources.
     */
    private String serviceName = "newnearsync-service";

    private final Otel otel = new Otel();
    private final Sentinel sentinel = new Sentinel();
    private Map<String, String> resourceAttributes = new HashMap<>();

    @Getter
    @Setter
    public static class Otel {
        private boolean enabled = true;
        /**
         * Instrumentation scope name for tracer.
         */
        private String instrumentationScope = "monitoring";
        private String endpoint = "http://localhost:4317";
        private boolean exporterEnabled = true;
        private Duration exporterTimeout = Duration.ofSeconds(5);
        private Duration scheduleDelay = Duration.ofSeconds(5);
    }

    @Getter
    @Setter
    public static class Sentinel {
        private boolean enabled = true;
        private List<FlowRuleConfig> flowRules = new ArrayList<>();
        private List<DegradeRuleConfig> degradeRules = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FlowRuleConfig {
        private String resource;
        private int grade = 1;
        private double count = 100d;
        private int controlBehavior = 0;
        private int maxQueueingTimeMs = 0;
        private int strategy = 0;
        private String refResource;
        private int warmUpPeriodSec = 10;
        private String limitApp = "default";
    }

    @Getter
    @Setter
    public static class DegradeRuleConfig {
        private String resource;
        private int grade = 0;
        private double count = 1d;
        private int timeWindow = 10;
        private int minRequestAmount = 5;
        private double slowRatioThreshold = 1d;
        private int statIntervalMs = 1000;
    }
}

