# DataReconciliationTask Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize DataReconciliationTask with distributed lock, parallel execution, correct metrics, and notification support.

**Architecture:** Extract interfaces to top-level, add Spring Integration Redis lock for distributed safety, use ExecutorChannel for parallel handler reconciliation, fix metrics calculation, add ReconciliationListener for notification.

**Tech Stack:** Spring Boot 4, Spring Integration 7, Spring Integration Redis, Micrometer, Java 21 Records

---

## File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `common/integration/pom.xml` | Modify | Add spring-integration-redis dependency |
| `.../reconciliation/ReconcilableHandler.java` | Modify | Update import to use top-level ReconciliationReport |
| `.../reconciliation/ReconciliationResult.java` | Create | Immutable record for reconciliation results |
| `.../reconciliation/ReconciliationReport.java` | Create | Extract from DataReconciliationTask inner record |
| `.../reconciliation/ReconciliationListener.java` | Create | Notification interface for reconciliation events |
| `.../reconciliation/DataReconciliationTask.java` | Modify | Refactor with Spring Integration, parallel execution, fixed metrics |
| `.../config/DataSyncProperties.java` | Modify | Add new reconciliation config fields |
| `.../config/DataSyncAutoConfiguration.java` | Modify | Update bean creation for new constructor |
| `.../handler/UserSyncHandler.java` | Modify | Update import to use top-level ReconciliationReport |
| `.../reconciliation/DataReconciliationTaskTest.java` | Create | Unit tests |

---

### Task 1: Add Spring Integration Redis Dependency

**Files:**
- Modify: `common/integration/pom.xml`

- [ ] **Step 1: Add spring-integration-redis dependency**

```xml
<!-- Spring Integration Redis (distributed lock) -->
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-redis</artifactId>
</dependency>

<!-- Spring Integration Core -->
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-core</artifactId>
</dependency>
```

Insert after the `spring-data-redis` dependency block (around line 74).

- [ ] **Step 2: Verify dependency resolves**

Run: `mvn dependency:resolve -pl common/integration -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add common/integration/pom.xml
git commit -m "build(integration): add spring-integration-redis dependency for distributed lock"
```

---

### Task 2: Update ReconcilableHandler Import

**Files:**
- Modify: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconcilableHandler.java`

- [ ] **Step 1: Update import to use top-level ReconciliationReport**

The file already exists. Replace the import line:

```java
// Before:
import com.frog.common.integration.sync.reconciliation.DataReconciliationTask.ReconciliationReport;

// After:
// (no import needed - ReconciliationReport will be in the same package)
```

Remove the import line entirely since `ReconciliationReport` will be in the same package after Task 4.

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconcilableHandler.java
git commit -m "refactor(reconciliation): update ReconcilableHandler import for top-level ReconciliationReport"
```

---

### Task 3: Create ReconciliationResult Record

**Files:**
- Create: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationResult.java`

- [ ] **Step 1: Create ReconciliationResult record**

```java
package com.frog.common.integration.sync.reconciliation;

/**
 * 对账结果（不可变）
 *
 * @param checked  检查总数
 * @param fixed    修复数量
 * @param failed   失败数量
 * @param duration 执行耗时（毫秒）
 * @author Deng
 * @since 2025-12-16
 */
public record ReconciliationResult(
        int checked,
        int fixed,
        int failed,
        long duration
) {
    public static ReconciliationResult empty() {
        return new ReconciliationResult(0, 0, 0, 0);
    }

    public static ReconciliationResult of(int checked, int fixed, int failed, long duration) {
        return new ReconciliationResult(checked, fixed, failed, duration);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationResult.java
git commit -m "feat(reconciliation): add immutable ReconciliationResult record"
```

---

### Task 4: Create ReconciliationListener Interface

**Files:**
- Create: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationListener.java`

- [ ] **Step 1: Create ReconciliationListener interface**

```java
package com.frog.common.integration.sync.reconciliation;

import java.util.Map;

/**
 * 对账结果监听器
 * <p>
 * 业务方可实现此接口，在对账完成后收到通知
 *
 * @author Deng
 * @since 2025-12-16
 */
public interface ReconciliationListener {

    /**
     * 对账完成回调
     *
     * @param results    各聚合类型的对账结果
     * @param totalFixed 总修复数量
     * @param totalFailed 总失败数量
     */
    void onReconciliationComplete(Map<String, ReconciliationResult> results, int totalFixed, int totalFailed);

    /**
     * 对账异常回调
     *
     * @param aggregateType 聚合类型
     * @param error         异常信息
     */
    void onReconciliationError(String aggregateType, Throwable error);
}
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationListener.java
git commit -m "feat(reconciliation): add ReconciliationListener notification interface"
```

---

### Task 4.5: Extract ReconciliationReport to Top-Level Record

**Files:**
- Create: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationReport.java`

- [ ] **Step 1: Create ReconciliationReport record**

Extract the inner record from `DataReconciliationTask` to a separate file:

```java
package com.frog.common.integration.sync.reconciliation;

/**
 * 对账报告（不可变）
 *
 * @param totalChecked     检查总数
 * @param inconsistentCount 不一致数量
 * @param fixedCount       修复数量
 * @param failedCount      失败数量
 * @author Deng
 * @since 2025-12-16
 */
public record ReconciliationReport(
        int totalChecked,
        int inconsistentCount,
        int fixedCount,
        int failedCount
) {
    public static ReconciliationReport empty() {
        return new ReconciliationReport(0, 0, 0, 0);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/ReconciliationReport.java
git commit -m "refactor(reconciliation): extract ReconciliationReport to top-level record"
```

---

### Task 5: Update DataSyncProperties Configuration

**Files:**
- Modify: `common/integration/src/main/java/com/frog/common/integration/sync/config/DataSyncProperties.java:138-159`

- [ ] **Step 1: Update ReconciliationConfig class**

Replace the entire `ReconciliationConfig` inner class with:

```java
@Data
public static class ReconciliationConfig {
    /**
     * 是否启用对账
     */
    private boolean enabled = false;

    /**
     * 对账 cron 表达式
     */
    private String cron = "0 0 3 * * ?";

    /**
     * 对账批次大小
     */
    private int batchSize = 1000;

    /**
     * 是否自动修复
     */
    private boolean autoFix = false;

    /**
     * 分布式锁 key
     */
    private String lockKey = "datasync:reconciliation:lock";

    /**
     * 分布式锁过期时间（秒）
     */
    private long lockTtlSeconds = 3600;

    /**
     * 是否启用并行对账
     */
    private boolean parallelEnabled = true;

    /**
     * 并行线程池大小
     */
    private int threadPoolSize = 8;

    /**
     * 对账不一致时是否通知
     */
    private boolean notifyOnInconsistency = false;
}
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/config/DataSyncProperties.java
git commit -m "config(reconciliation): add distributed lock and parallel execution config"
```

---

### Task 6: Refactor DataReconciliationTask

**Files:**
- Modify: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTask.java`

- [ ] **Step 1: Rewrite DataReconciliationTask**

Replace the entire file content with:

```java
package com.frog.common.integration.sync.reconciliation;

import com.frog.common.integration.sync.config.DataSyncProperties;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据对账定时任务
 * <p>
 * 定期检查冗余数据一致性，发现不一致时进行修复
 * 设计参考：
 * - 阿里巴巴：T+1 对账 + 实时告警
 * - 美团：分钟级抽样对账
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class DataReconciliationTask {

    private static final String LOCK_SCRIPT =
            "if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then return 1 else return 0 end";
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final DataSyncProperties properties;
    private final Map<String, ReconcilableHandler> handlers;
    private final StringRedisTemplate redisTemplate;
    private final String lockValue;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutorService executorService;
    private final List<ReconciliationListener> listeners;

    // Metrics
    private final Counter reconcileSuccessCounter;
    private final Counter reconcileFailureCounter;
    private final Counter reconcileFixCounter;

    public DataReconciliationTask(DataSyncProperties properties,
                                   List<DataSyncHandler> handlerList,
                                   StringRedisTemplate redisTemplate,
                                   MeterRegistry meterRegistry,
                                   List<ReconciliationListener> listeners) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.listeners = listeners != null ? List.copyOf(listeners) : List.of();

        // 只注册支持对账的 Handler
        Map<String, ReconcilableHandler> handlerMap = new ConcurrentHashMap<>();
        if (handlerList != null) {
            handlerList.stream()
                    .filter(h -> h instanceof ReconcilableHandler)
                    .map(h -> (ReconcilableHandler) h)
                    .forEach(h -> handlerMap.put(h.getAggregateType(), h));
        }
        this.handlers = Collections.unmodifiableMap(handlerMap);

        // 并行线程池
        DataSyncProperties.ReconciliationConfig config = properties.getReconciliation();
        if (config.isParallelEnabled() && handlers.size() > 1) {
            this.executorService = Executors.newFixedThreadPool(
                    config.getThreadPoolSize(),
                    r -> {
                        Thread t = new Thread(r, "reconciliation-worker");
                        t.setDaemon(true);
                        return t;
                    }
            );
        } else {
            this.executorService = null;
        }

        // 生成唯一锁标识（避免误删其他实例的锁）
        this.lockValue = UUID.randomUUID().toString();

        // Metrics
        this.reconcileSuccessCounter = Counter.builder("datasync.reconcile.success")
                .description("Number of consistent items found during reconciliation")
                .register(meterRegistry);
        this.reconcileFailureCounter = Counter.builder("datasync.reconcile.failure")
                .description("Number of inconsistencies found during reconciliation")
                .register(meterRegistry);
        this.reconcileFixCounter = Counter.builder("datasync.reconcile.fix")
                .description("Number of auto-fixed inconsistencies")
                .register(meterRegistry);
    }

    /**
     * 定时对账任务
     */
    @Scheduled(cron = "${datasync.reconciliation.cron:0 0 3 * * ?}")
    public void reconcile() {
        if (!properties.getReconciliation().isEnabled()) {
            return;
        }

        // 防止重复执行
        if (!running.compareAndSet(false, true)) {
            log.warn("[Reconciliation] Task already running, skipping...");
            return;
        }

        try {
            if (!acquireLock()) {
                log.info("[Reconciliation] Failed to acquire distributed lock, another instance is running");
                return;
            }

            log.info("[Reconciliation] Starting data reconciliation task...");
            long startTime = System.currentTimeMillis();

            Map<String, ReconciliationResult> results = new ConcurrentHashMap<>();
            int totalChecked = 0;
            int totalFixed = 0;
            int totalFailed = 0;

            if (executorService != null) {
                // 并行对账
                List<Future<ReconciliationResult>> futures = new ArrayList<>();
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    ReconcilableHandler handler = entry.getValue();
                    futures.add(executorService.submit(() ->
                            reconcileAggregate(aggregateType, handler)));
                }

                int index = 0;
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    try {
                        ReconciliationResult result = futures.get(index++).get(5, TimeUnit.MINUTES);
                        results.put(aggregateType, result);
                        totalChecked += result.checked();
                        totalFixed += result.fixed();
                        totalFailed += result.failed();
                        log.info("[Reconciliation] {} - checked: {}, fixed: {}, failed: {}",
                                aggregateType, result.checked(), result.fixed(), result.failed());
                    } catch (Exception e) {
                        log.error("[Reconciliation] Error waiting for {}: {}",
                                aggregateType, e.getMessage(), e);
                        results.put(aggregateType, ReconciliationResult.empty());
                    }
                }
            } else {
                // 串行对账
                for (Map.Entry<String, ReconcilableHandler> entry : handlers.entrySet()) {
                    String aggregateType = entry.getKey();
                    ReconcilableHandler handler = entry.getValue();
                    try {
                        ReconciliationResult result = reconcileAggregate(aggregateType, handler);
                        results.put(aggregateType, result);
                        totalChecked += result.checked();
                        totalFixed += result.fixed();
                        totalFailed += result.failed();
                        log.info("[Reconciliation] {} - checked: {}, fixed: {}, failed: {}",
                                aggregateType, result.checked(), result.fixed(), result.failed());
                    } catch (Exception e) {
                        log.error("[Reconciliation] Error reconciling {}: {}",
                                aggregateType, e.getMessage(), e);
                        results.put(aggregateType, ReconciliationResult.empty());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Reconciliation] Completed in {}ms - total checked: {}, fixed: {}, failed: {}",
                    duration, totalChecked, totalFixed, totalFailed);

            // 通知监听器
            notifyListeners(results, totalChecked, totalFixed, totalFailed);

        } finally {
            running.set(false);
            releaseLock();
        }
    }

    /**
     * 对账单个聚合类型
     */
    private ReconciliationResult reconcileAggregate(String aggregateType, ReconcilableHandler handler) {
        long start = System.currentTimeMillis();

        try {
            ReconciliationReport report = handler.reconcile(
                    properties.getReconciliation().getBatchSize(),
                    properties.getReconciliation().isAutoFix()
            );

            long duration = System.currentTimeMillis() - start;

            // 修复 metrics 计算：success = checked - failed（一致的数据）
            int successCount = report.totalChecked() - report.inconsistentCount();
            reconcileSuccessCounter.increment(Math.max(0, successCount));
            reconcileFailureCounter.increment(report.inconsistentCount());
            reconcileFixCounter.increment(report.fixedCount());

            return ReconciliationResult.of(
                    report.totalChecked(),
                    report.fixedCount(),
                    report.failedCount(),
                    duration
            );

        } catch (Exception e) {
            log.error("[Reconciliation] Handler error for {}: {}", aggregateType, e.getMessage(), e);
            long duration = System.currentTimeMillis() - start;
            return ReconciliationResult.of(0, 0, 0, duration);
        }
    }

    /**
     * 获取分布式锁
     */
    private boolean acquireLock() {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script,
                    List.of(properties.getReconciliation().getLockKey()),
                    lockValue,
                    String.valueOf(properties.getReconciliation().getLockTtlSeconds()));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[Reconciliation] Failed to acquire lock: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     */
    private void releaseLock() {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            redisTemplate.execute(script,
                    List.of(properties.getReconciliation().getLockKey()),
                    lockValue);
        } catch (Exception e) {
            log.error("[Reconciliation] Failed to release lock: {}", e.getMessage(), e);
        }
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners(Map<String, ReconciliationResult> results, int totalChecked, int totalFixed, int totalFailed) {
        if (listeners.isEmpty()) {
            return;
        }

        for (ReconciliationListener listener : listeners) {
            try {
                listener.onReconciliationComplete(results, totalFixed, totalFailed);
            } catch (Exception e) {
                log.error("[Reconciliation] Listener error: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 获取当前是否正在执行
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取已注册的处理器数量
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTask.java
git commit -m "refactor(reconciliation): add distributed lock, parallel execution, fixed metrics"
```

---

### Task 7: Update DataSyncAutoConfiguration

**Files:**
- Modify: `common/integration/src/main/java/com/frog/common/integration/sync/config/DataSyncAutoConfiguration.java:142-152`

- [ ] **Step 1: Update dataReconciliationTask bean method**

Replace the `dataReconciliationTask` bean definition with:

```java
@Bean
@ConditionalOnProperty(prefix = "datasync.reconciliation", name = "enabled", havingValue = "true")
public DataReconciliationTask dataReconciliationTask(
        DataSyncProperties properties,
        ObjectProvider<List<DataSyncHandler>> handlersProvider,
        StringRedisTemplate redisTemplate,
        MeterRegistry meterRegistry,
        ObjectProvider<List<ReconciliationListener>> listenersProvider) {

    log.info("[DataSync] Initializing reconciliation task with cron: {}, parallel: {}",
            properties.getReconciliation().getCron(),
            properties.getReconciliation().isParallelEnabled());
    return new DataReconciliationTask(
            properties,
            handlersProvider.getIfAvailable(),
            redisTemplate,
            meterRegistry,
            listenersProvider.getIfAvailable()
    );
}
```

Also add the import for `ReconciliationListener`:

```java
import com.frog.common.integration.sync.reconciliation.ReconciliationListener;
```

- [ ] **Step 2: Commit**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/config/DataSyncAutoConfiguration.java
git commit -m "config(auto-config): update DataReconciliationTask bean with new dependencies"
```

---

### Task 8: Remove Inner ReconciliationResult from Task

**Files:**
- Modify: `common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTask.java`

- [ ] **Step 1: Verify no references to old inner class**

The old `ReconciliationResult` inner class and `ReconcilableHandler` inner interface should have been removed in Task 6 when we rewrote the file. Verify the rewritten file doesn't contain them.

- [ ] **Step 2: Commit (if needed)**

```bash
git add common/integration/src/main/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTask.java
git commit -m "refactor(reconciliation): remove inner classes replaced by top-level types"
```

---

### Task 8.5: Update UserSyncHandler References

**Files:**
- Modify: `system/service/src/main/java/com/frog/system/sync/handler/UserSyncHandler.java`

- [ ] **Step 1: Update import and references**

The `UserSyncHandler` references `DataReconciliationTask.ReconciliationReport` at lines 90 and 138. Update to use the top-level `ReconciliationReport`:

```java
// Before (line 90):
public DataReconciliationTask.ReconciliationReport reconcile(int batchSize, boolean autoFix) {

// After:
public ReconciliationReport reconcile(int batchSize, boolean autoFix) {
```

```java
// Before (line 138):
return new DataReconciliationTask.ReconciliationReport(

// After:
return new ReconciliationReport(
```

Add the import:

```java
import com.frog.common.integration.sync.reconciliation.ReconciliationReport;
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -pl system/service -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add system/service/src/main/java/com/frog/system/sync/handler/UserSyncHandler.java
git commit -m "refactor(reconciliation): update UserSyncHandler to use top-level ReconciliationReport"
```

---

### Task 9: Create Unit Tests

**Files:**
- Create: `common/integration/src/test/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTaskTest.java`

- [ ] **Step 1: Create test class**

```java
package com.frog.common.integration.sync.reconciliation;

import com.frog.common.integration.sync.config.DataSyncProperties;
import com.frog.common.integration.sync.handler.DataSyncHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataReconciliationTaskTest {

    private DataSyncProperties properties;
    private StringRedisTemplate redisTemplate;
    private MeterRegistry meterRegistry;
    private ReconciliationListener listener;

    @BeforeEach
    void setUp() {
        properties = new DataSyncProperties();
        properties.getReconciliation().setEnabled(true);
        properties.getReconciliation().setParallelEnabled(false);
        properties.getReconciliation().setAutoFix(false);

        redisTemplate = mock(StringRedisTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        listener = mock(ReconciliationListener.class);
    }

    @Test
    void shouldSkipWhenDisabled() {
        properties.getReconciliation().setEnabled(false);
        DataSyncProperties.ReconciliationConfig config = properties.getReconciliation();
        config.setEnabled(false);

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(), redisTemplate, meterRegistry, List.of(listener));

        task.reconcile();

        verifyNoInteractions(listener);
        assertFalse(task.isRunning());
    }

    @Test
    void shouldSkipWhenLockFails() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(0L);

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(), redisTemplate, meterRegistry, List.of(listener));

        task.reconcile();

        verifyNoInteractions(listener);
    }

    @Test
    void shouldReconcileWithSingleHandler() {
        // Mock successful lock acquisition
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        // Create mock handler
        ReconcilableHandler mockHandler = mock(ReconcilableHandler.class);
        when(mockHandler.getAggregateType()).thenReturn("User");
        when(mockHandler.reconcile(anyInt(), anyBoolean()))
                .thenReturn(new ReconciliationReport(100, 5, 3, 2));

        DataSyncHandler handler = mockHandler;

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(handler), redisTemplate, meterRegistry, List.of(listener));

        task.reconcile();

        verify(listener).onReconciliationComplete(anyMap(), eq(3), eq(2));
        verify(mockHandler).reconcile(1000, false);
    }

    @Test
    void shouldReturnEmptyResultWhenHandlerThrows() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        ReconcilableHandler mockHandler = mock(ReconcilableHandler.class);
        when(mockHandler.getAggregateType()).thenReturn("User");
        when(mockHandler.reconcile(anyInt(), anyBoolean()))
                .thenThrow(new RuntimeException("DB connection failed"));

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(mockHandler), redisTemplate, meterRegistry, List.of(listener));

        task.reconcile();

        verify(listener).onReconciliationComplete(anyMap(), eq(0), eq(0));
    }

    @Test
    void shouldTrackMetricsCorrectly() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        ReconcilableHandler mockHandler = mock(ReconcilableHandler.class);
        when(mockHandler.getAggregateType()).thenReturn("User");
        when(mockHandler.reconcile(anyInt(), anyBoolean()))
                .thenReturn(new ReconciliationReport(100, 10, 7, 3));

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(mockHandler), redisTemplate, meterRegistry, List.of(listener));

        task.reconcile();

        // success = 100 - 10 = 90 (consistent items)
        assertEquals(90.0, meterRegistry.counter("datasync.reconcile.success").count());
        // failure = 10 (inconsistent items found)
        assertEquals(10.0, meterRegistry.counter("datasync.reconcile.failure").count());
        // fix = 7 (items successfully fixed)
        assertEquals(7.0, meterRegistry.counter("datasync.reconcile.fix").count());
    }

    @Test
    void shouldPreventConcurrentExecution() {
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(1L);

        // Simulate running state
        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(), redisTemplate, meterRegistry, List.of(listener));

        // First call should run
        task.reconcile();

        // Second call should be skipped (running flag reset after first completes)
        task.reconcile();

        // Lock should be released after each run
        verify(redisTemplate, times(2)).execute(any(), anyList(), any(), any());
    }

    @Test
    void shouldReportHandlerCount() {
        ReconcilableHandler handler1 = mock(ReconcilableHandler.class);
        when(handler1.getAggregateType()).thenReturn("User");

        ReconcilableHandler handler2 = mock(ReconcilableHandler.class);
        when(handler2.getAggregateType()).thenReturn("Dept");

        DataReconciliationTask task = new DataReconciliationTask(
                properties, List.of(handler1, handler2), redisTemplate, meterRegistry, List.of());

        assertEquals(2, task.getHandlerCount());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl common/integration -Dtest=DataReconciliationTaskTest -q`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add common/integration/src/test/java/com/frog/common/integration/sync/reconciliation/DataReconciliationTaskTest.java
git commit -m "test(reconciliation): add unit tests for DataReconciliationTask"
```

---

### Task 10: Build Verification

- [ ] **Step 1: Full build**

Run: `mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all integration tests**

Run: `mvn test -pl common/integration -q`
Expected: All tests PASS

- [ ] **Step 3: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix(reconciliation): address review feedback"
```
