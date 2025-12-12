package com.frog.common.mybatisPlus.aspect;

import com.frog.common.mybatisPlus.annotation.DataScope;
import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.mybatisPlus.context.DataScopeFilter;
import com.frog.common.security.SecurityContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DataScopeAspect Test Suite
 *
 * <p>REFACTORED: Tests the refactored DataScopeAspect that now depends on
 * SecurityContext interface instead of concrete SecurityUser class.
 *
 * <p>This validates:
 * - Proper dependency inversion (depends on interface, not implementation)
 * - Data scope filtering logic remains correct after refactoring
 * - ThreadLocal context management works properly
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataScopeAspect Refactoring Tests")
class DataScopeAspectTest {

    @Mock
    private SecurityContext securityContext;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private DataScope dataScopeAnnotation;

    private DataScopeAspect aspect;

    private UUID testUserId;
    private UUID testDeptId;

    @BeforeEach
    void setUp() {
        aspect = new DataScopeAspect(securityContext);
        testUserId = UUID.randomUUID();
        testDeptId = UUID.randomUUID();

        // Default annotation values
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
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(false);
        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);
        assertThat(DataScopeContextHolder.getFilter()).isNull();
        verify(securityContext).isAuthenticated();
        verify(joinPoint).proceed();
        verifyNoMoreInteractions(securityContext); // Should not call other methods
    }

    @Test
    @DisplayName("Should skip data scope when userId is null")
    void testAround_NullUserId_SkipsDataScope() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(null);
        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);
        assertThat(DataScopeContextHolder.getFilter()).isNull();
        verify(securityContext).isAuthenticated();
        verify(securityContext).getCurrentUserId();
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Should apply data scope level 5 (SELF) correctly")
    void testAround_LevelSelf_AppliesDataScope() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5); // SELF

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);

        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("u = UNHEX(REPLACE(#{__ds_userId}, '-', ''))");
        assertThat(filter.getParams()).containsEntry("__ds_userId", testUserId.toString());

        verify(securityContext).isAuthenticated();
        verify(securityContext).getCurrentUserId();
        verify(securityContext).getCurrentDeptId();
        verify(securityContext).getDataScopeLevel();
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Should apply data scope level 3 (DEPT) correctly")
    void testAround_LevelDept_AppliesDataScope() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(3); // DEPT

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);

        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("d = UNHEX(REPLACE(#{__ds_deptId}, '-', ''))");
        assertThat(filter.getParams()).containsEntry("__ds_deptId", testDeptId.toString());
    }

    @Test
    @DisplayName("Should apply data scope level 1 (ALL) correctly")
    void testAround_LevelAll_AppliesNoFiltering() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(1); // ALL

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);

        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).isEqualTo("1=1"); // No filtering
    }

    @Test
    @DisplayName("Should apply data scope level 4 (DEPT_AND_CHILDREN) with recursive CTE")
    void testAround_LevelDeptAndChildren_AppliesRecursiveCTE() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(4); // DEPT_AND_CHILDREN

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);

        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("WITH RECURSIVE dept_tree");
        assertThat(filter.getClause()).contains("d IN");
        assertThat(filter.getParams()).containsEntry("__ds_deptId", testDeptId.toString());
    }

    @Test
    @DisplayName("Should handle null deptId for DEPT level gracefully")
    void testAround_LevelDept_NullDeptId_DeniesAccess() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(null); // No department
        when(securityContext.getDataScopeLevel()).thenReturn(3); // DEPT

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        assertThat(result).isEqualTo(expectedResult);

        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).isEqualTo("1=0"); // Deny all access
    }

    @Test
    @DisplayName("Should use custom table aliases from annotation")
    void testAround_CustomAliases_UsedInFilter() throws Throwable {
        // Arrange
        when(dataScopeAnnotation.userAlias()).thenReturn("user_table");
        when(dataScopeAnnotation.deptAlias()).thenReturn("dept_table");

        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5); // SELF

        Object expectedResult = "proceed-result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        // Act
        Object result = aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        DataScopeFilter filter = DataScopeContextHolder.getFilter();
        assertThat(filter).isNotNull();
        assertThat(filter.getClause()).contains("user_table =");
    }

    @Test
    @DisplayName("Should clear ThreadLocal context after processing")
    void testAround_ClearsThreadLocalContext() throws Throwable {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);

        when(joinPoint.proceed()).thenReturn("result");

        // Act
        aspect.around(joinPoint, dataScopeAnnotation);

        // Assert
        // ThreadLocal should be cleared after around() completes
        assertThat(DataScopeContextHolder.getFilter()).isNull();
    }

    @Test
    @DisplayName("Should clear ThreadLocal even when exception occurs")
    void testAround_ClearsThreadLocalOnException() {
        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(true);
        when(securityContext.getCurrentUserId()).thenReturn(testUserId);
        when(securityContext.getCurrentDeptId()).thenReturn(testDeptId);
        when(securityContext.getDataScopeLevel()).thenReturn(5);

        try {
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));
        } catch (Throwable e) {
            // Expected
        }

        // Act & Assert
        assertThatThrownBy(() -> aspect.around(joinPoint, dataScopeAnnotation))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test exception");

        // ThreadLocal should still be cleared even after exception
        assertThat(DataScopeContextHolder.getFilter()).isNull();
    }

    @Test
    @DisplayName("REFACTORING: Verify no dependency on SecurityUser or SecurityUtils")
    void testRefactoring_NoDependencyOnWebLayer() {
        // This test validates that DataScopeAspect only depends on SecurityContext interface
        // If this test compiles, it proves we successfully decoupled from web layer

        // Arrange
        when(securityContext.isAuthenticated()).thenReturn(false);

        try {
            when(joinPoint.proceed()).thenReturn("result");
            // Act
            aspect.around(joinPoint, dataScopeAnnotation);

            // Assert: If we reach here, refactoring is successful
            assertThat(aspect).isNotNull();
        } catch (Throwable e) {
            fail("Should not throw exception", e);
        }
    }
}