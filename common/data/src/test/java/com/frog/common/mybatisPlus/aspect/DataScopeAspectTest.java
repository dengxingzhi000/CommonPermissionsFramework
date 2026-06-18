package com.frog.common.mybatisPlus.aspect;

import com.frog.common.mybatisPlus.annotation.DataScope;
import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.mybatisPlus.context.DataScopeFilter;
import com.frog.common.mybatisPlus.service.DataPermissionService;
import com.frog.common.security.SecurityContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DataScopeAspect Test Suite
 *
 * Tests the DataScopeAspect that depends on SecurityContext interface.
 * Validates data scope filtering logic and ThreadLocal context management.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataScopeAspect Tests")
class DataScopeAspectTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private DataPermissionService dataPermissionService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private DataScope dataScopeAnnotation;

    private DataScopeAspect aspect;

    private UUID testUserId;
    private UUID testDeptId;

    @BeforeEach
    void setUp() {
        aspect = new DataScopeAspect(securityContext, dataPermissionService);
        testUserId = UUID.randomUUID();
        testDeptId = UUID.randomUUID();

        when(dataScopeAnnotation.userAlias()).thenReturn("u");
        when(dataScopeAnnotation.deptAlias()).thenReturn("d");
    }

    @AfterEach
    void tearDown() {
        DataScopeContextHolder.clear();
    }

    @Test
    @DisplayName("Should skip data scope when user is not authenticated")
    void testAround_NotAuthenticated_SkipsDataScope() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(false);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        assertThat(result).isEqualTo("result");
        assertThat(DataScopeContextHolder.get()).isNull();
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Should skip data scope when userId is null")
    void testAround_NullUserId_SkipsDataScope() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        assertThat(result).isEqualTo("result");
        assertThat(DataScopeContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should apply level 5 (SELF) with PostgreSQL UUID syntax")
    void testAround_LevelSelf() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("u = #{__ds_userId}::uuid");
        assertThat(filter.getParams()).containsEntry("__ds_userId", testUserId.toString());
    }

    @Test
    @DisplayName("Should apply level 3 (DEPT) with PostgreSQL UUID syntax")
    void testAround_LevelDept() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(3);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("d = #{__ds_deptId}::uuid");
        assertThat(filter.getParams()).containsEntry("__ds_deptId", testDeptId.toString());
    }

    @Test
    @DisplayName("Should apply level 1 (ALL) with no filtering")
    void testAround_LevelAll() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(1);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).isEqualTo("1=1");
    }

    @Test
    @DisplayName("Should apply level 4 (DEPT_AND_CHILDREN) with recursive CTE")
    void testAround_LevelDeptAndChildren() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(4);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("WITH RECURSIVE dept_tree");
        assertThat(filter.getParams()).containsEntry("__ds_deptId", testDeptId.toString());
    }

    @Test
    @DisplayName("Should deny access when deptId is null for DEPT level")
    void testAround_LevelDept_NullDeptId() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(null);
        when(securityContext.getDataScopeLevel()).thenReturn(3);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).isEqualTo("1=0");
    }

    @Test
    @DisplayName("Should use custom aliases from annotation")
    void testAround_CustomAliases() throws Throwable {
        when(dataScopeAnnotation.userAlias()).thenReturn("user_table");
        when(dataScopeAnnotation.deptAlias()).thenReturn("dept_table");
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        DataScopeFilter filter = DataScopeContextHolder.get();
        assertThat(filter.getClause()).contains("user_table =");
    }

    @Test
    @DisplayName("Should clear ThreadLocal after processing")
    void testAround_ClearsThreadLocal() throws Throwable {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint, dataScopeAnnotation);

        assertThat(DataScopeContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should clear ThreadLocal even on exception")
    void testAround_ClearsThreadLocalOnException() {
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);
        try {
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Test"));
        } catch (Throwable ignored) {
        }

        assertThatThrownBy(() -> aspect.around(joinPoint, dataScopeAnnotation))
                .isInstanceOf(RuntimeException.class);

        assertThat(DataScopeContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("REFACTORING: Verify dependency on SecurityContext interface only")
    void testRefactoring_DependsOnInterface() {
        when(securityContext.isAuthenticated()).thenReturn(false);
        try {
            when(joinPoint.proceed()).thenReturn("result");
            aspect.around(joinPoint, dataScopeAnnotation);
            assertThat(aspect).isNotNull();
        } catch (Throwable e) {
            fail("Should not throw", e);
        }
    }
}
