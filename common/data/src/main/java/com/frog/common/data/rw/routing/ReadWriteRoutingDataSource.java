package com.frog.common.data.rw.routing;

import com.frog.common.data.rw.config.ReadWriteProperties;
import com.frog.common.data.rw.loadbalance.SlaveLoadBalancer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读写分离路由数据源
 * <p>
 * 基于 Spring AbstractRoutingDataSource 实现，支持：
 * - 主从自动路由
 * - 负载均衡
 * - 健康检查
 * - 读写一致性保证
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
    private static final String MASTER_KEY = "master";

    private final String groupName;
    private final ReadWriteProperties properties;
    private final SlaveLoadBalancer loadBalancer;

    @Setter
    private List<SlaveLoadBalancer.SlaveInfo> slaveInfos;

    /**
     * 从库可用性状态
     */
    private final Map<String, Boolean> slaveAvailability = new ConcurrentHashMap<>();

    // Metrics
    private Counter masterRouteCounter;
    private Counter slaveRouteCounter;
    private Counter fallbackCounter;

    public ReadWriteRoutingDataSource(String groupName,
                                       DataSource masterDataSource,
                                       Map<String, DataSource> slaveDataSources,
                                       ReadWriteProperties properties,
                                       SlaveLoadBalancer loadBalancer,
                                       MeterRegistry meterRegistry) {
        this.groupName = groupName;
        this.properties = properties;
        this.loadBalancer = loadBalancer;

        // 设置数据源
        Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();
        targetDataSources.put(MASTER_KEY, masterDataSource);
        targetDataSources.putAll(slaveDataSources);

        setTargetDataSources(targetDataSources);
        setDefaultTargetDataSource(masterDataSource);

        // 初始化从库可用性
        slaveDataSources.keySet().forEach(name -> slaveAvailability.put(name, true));

        // 初始化指标
        if (meterRegistry != null) {
            initMetrics(meterRegistry);
        }
    }

    private void initMetrics(MeterRegistry meterRegistry) {
        this.masterRouteCounter = Counter.builder("datasource.rw.route")
                .tag("group", groupName)
                .tag("target", "master")
                .description("Number of routes to master")
                .register(meterRegistry);

        this.slaveRouteCounter = Counter.builder("datasource.rw.route")
                .tag("group", groupName)
                .tag("target", "slave")
                .description("Number of routes to slave")
                .register(meterRegistry);

        this.fallbackCounter = Counter.builder("datasource.rw.fallback")
                .tag("group", groupName)
                .description("Number of fallbacks to master")
                .register(meterRegistry);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // 1. 检查是否应该使用主库
        long readMasterAfterWriteMs = properties.getReadMasterAfterWrite().toMillis();
        if (ReadWriteRoutingContext.shouldUseMaster(readMasterAfterWriteMs)) {
            log.debug("[RW-Routing] Group [{}] routing to MASTER", groupName);
            incrementMasterCounter();
            return MASTER_KEY;
        }

        // 2. 检查路由类型
        ReadWriteRoutingContext.RoutingType routingType = ReadWriteRoutingContext.current();

        if (routingType == ReadWriteRoutingContext.RoutingType.MASTER) {
            log.debug("[RW-Routing] Group [{}] routing to MASTER (explicit)", groupName);
            incrementMasterCounter();
            return MASTER_KEY;
        }

        // 3. 尝试路由到从库
        if (routingType == ReadWriteRoutingContext.RoutingType.SLAVE ||
                routingType == ReadWriteRoutingContext.RoutingType.AUTO) {

            // 检查是否指定了特定从库
            String specifiedSlave = ReadWriteRoutingContext.getSpecifiedSlave();
            if (specifiedSlave != null && slaveAvailability.getOrDefault(specifiedSlave, false)) {
                log.debug("[RW-Routing] Group [{}] routing to SLAVE [{}] (specified)",
                        groupName, specifiedSlave);
                incrementSlaveCounter();
                return specifiedSlave;
            }

            // 使用负载均衡选择从库
            String selectedSlave = selectSlave();
            if (selectedSlave != null) {
                log.debug("[RW-Routing] Group [{}] routing to SLAVE [{}]",
                        groupName, selectedSlave);
                incrementSlaveCounter();
                return selectedSlave;
            }

            // 从库不可用，降级到主库
            log.warn("[RW-Routing] Group [{}] no available slave, fallback to MASTER", groupName);
            incrementFallbackCounter();
        }

        incrementMasterCounter();
        return MASTER_KEY;
    }

    private String selectSlave() {
        if (slaveInfos == null || slaveInfos.isEmpty()) {
            return null;
        }

        // 过滤可用的从库
        List<SlaveLoadBalancer.SlaveInfo> availableSlaves = slaveInfos.stream()
                .filter(s -> slaveAvailability.getOrDefault(s.name(), false))
                .toList();

        if (availableSlaves.isEmpty()) {
            return null;
        }

        return loadBalancer.select(availableSlaves);
    }

    /**
     * 标记从库不可用
     */
    public void markSlaveUnavailable(String slaveName) {
        slaveAvailability.put(slaveName, false);
        log.warn("[RW-Routing] Group [{}] slave [{}] marked as UNAVAILABLE",
                groupName, slaveName);
    }

    /**
     * 标记从库可用
     */
    public void markSlaveAvailable(String slaveName) {
        slaveAvailability.put(slaveName, true);
        log.info("[RW-Routing] Group [{}] slave [{}] marked as AVAILABLE",
                groupName, slaveName);
    }

    /**
     * 获取从库可用性状态
     */
    public Map<String, Boolean> getSlaveAvailability() {
        return Map.copyOf(slaveAvailability);
    }

    private void incrementMasterCounter() {
        if (masterRouteCounter != null) {
            masterRouteCounter.increment();
        }
    }

    private void incrementSlaveCounter() {
        if (slaveRouteCounter != null) {
            slaveRouteCounter.increment();
        }
    }

    private void incrementFallbackCounter() {
        if (fallbackCounter != null) {
            fallbackCounter.increment();
        }
    }
}
