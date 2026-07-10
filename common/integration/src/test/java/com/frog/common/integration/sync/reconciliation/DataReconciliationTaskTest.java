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
