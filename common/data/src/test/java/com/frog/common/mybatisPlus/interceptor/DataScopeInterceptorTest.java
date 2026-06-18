package com.frog.common.mybatisPlus.interceptor;

import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.mybatisPlus.context.DataScopeFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * DataScopeInterceptor Test Suite
 *
 * Tests ThreadLocal context management and filter lifecycle.
 * SQL injection prevention is validated at the DataScopeAspect level
 * (validateSqlIdentifier) and DataScopeInterceptor level (isSafeFilter).
 */
@DisplayName("Data Scope Context and Filter Tests")
class DataScopeInterceptorTest {

    @AfterEach
    void tearDown() {
        DataScopeContextHolder.clear();
    }

    @Test
    @DisplayName("Should store and retrieve data scope filter")
    void testContextSetAndGet() {
        UUID userId = UUID.randomUUID();
        DataScopeFilter filter = new DataScopeFilter(
                "d.dept_id = #{__ds_deptId}::uuid",
                Map.of("__ds_deptId", UUID.randomUUID().toString())
        );

        DataScopeContextHolder.set(filter);
        DataScopeFilter retrieved = DataScopeContextHolder.get();

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getClause()).contains("dept_id");
        assertThat(retrieved.getParams()).containsKey("__ds_deptId");
    }

    @Test
    @DisplayName("Should return null when no context is set")
    void testContextNullWhenEmpty() {
        assertThat(DataScopeContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should clear context properly")
    void testContextCleanup() {
        DataScopeFilter filter = new DataScopeFilter("1=1", Map.of());
        DataScopeContextHolder.set(filter);

        DataScopeContextHolder.clear();

        assertThat(DataScopeContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should support different scope levels")
    void testDifferentScopeLevels() {
        // ALL data
        DataScopeFilter allFilter = new DataScopeFilter("1=1", Map.of());
        DataScopeContextHolder.set(allFilter);
        assertThat(DataScopeContextHolder.get().getClause()).isEqualTo("1=1");
        DataScopeContextHolder.clear();

        // SELF only
        UUID userId = UUID.randomUUID();
        DataScopeFilter selfFilter = new DataScopeFilter(
                "u.create_by = #{__ds_userId}::uuid",
                Map.of("__ds_userId", userId.toString())
        );
        DataScopeContextHolder.set(selfFilter);
        assertThat(DataScopeContextHolder.get().getClause()).contains("create_by");
        DataScopeContextHolder.clear();

        // NO access
        DataScopeFilter noAccessFilter = new DataScopeFilter("1=0", Map.of());
        DataScopeContextHolder.set(noAccessFilter);
        assertThat(DataScopeContextHolder.get().getClause()).isEqualTo("1=0");
    }

    @Test
    @DisplayName("Should handle custom dept list filter")
    void testCustomDeptFilter() {
        UUID deptId1 = UUID.randomUUID();
        UUID deptId2 = UUID.randomUUID();
        String clause = String.format(
                "(d.dept_id IN (#{__ds_deptId_0}::uuid,#{__ds_deptId_1}::uuid) OR u.create_by = #{__ds_userId}::uuid)");
        Map<String, Object> params = Map.of(
                "__ds_deptId_0", deptId1.toString(),
                "__ds_deptId_1", deptId2.toString(),
                "__ds_userId", UUID.randomUUID().toString()
        );

        DataScopeFilter filter = new DataScopeFilter(clause, params);
        DataScopeContextHolder.set(filter);

        DataScopeFilter retrieved = DataScopeContextHolder.get();
        assertThat(retrieved.getClause()).contains("IN");
        assertThat(retrieved.getParams()).hasSize(3);
    }

    @Test
    @DisplayName("Should handle recursive CTE filter")
    void testRecursiveCTEFilter() {
        String clause = """
                d.dept_id IN (
                    WITH RECURSIVE dept_tree AS (
                        SELECT id FROM sys_dept WHERE id = #{__ds_deptId}::uuid AND NOT deleted
                        UNION ALL
                        SELECT d.id FROM sys_dept d
                        INNER JOIN dept_tree dt ON d.parent_id = dt.id
                        WHERE NOT d.deleted
                    )
                    SELECT id FROM dept_tree
                )
                """;
        DataScopeFilter filter = new DataScopeFilter(clause, Map.of("__ds_deptId", UUID.randomUUID().toString()));
        DataScopeContextHolder.set(filter);

        assertThat(DataScopeContextHolder.get().getClause()).contains("WITH RECURSIVE");
    }

    @Test
    @DisplayName("Filter params should be mutable for interceptor injection")
    void testFilterParamsMutable() {
        DataScopeFilter filter = new DataScopeFilter("1=1", new java.util.HashMap<>());
        filter.getParams().put("key", "value");

        assertThat(filter.getParams()).containsEntry("key", "value");
    }
}
