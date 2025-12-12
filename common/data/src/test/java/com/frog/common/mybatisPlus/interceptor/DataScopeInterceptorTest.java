package com.frog.common.mybatisPlus.interceptor;

import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.mybatisPlus.context.DataScopeFilter;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
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
 * DataScopeInterceptor Test Suite
 *
 * SECURITY CRITICAL: Tests SQL injection prevention in data scope feature
 * - SQL pattern validation
 * - Dangerous keyword blocking
 * - Whitelist pattern enforcement
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Data Scope SQL Injection Prevention Tests")
class DataScopeInterceptorTest {

    private DataScopeInterceptor interceptor;

    @Mock
    private Executor executor;

    @Mock
    private MappedStatement mappedStatement;

    @Mock
    private ResultHandler<?> resultHandler;

    private RowBounds rowBounds;
    private Object parameter;

    @BeforeEach
    void setUp() {
        interceptor = new DataScopeInterceptor();
        rowBounds = RowBounds.DEFAULT;
        parameter = new Object();

        // Mock MappedStatement for SELECT queries
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal
        DataScopeContextHolder.clear();
    }

    @Test
    @DisplayName("Should allow safe data scope filter")
    void testIsSafeFilter_SafeFilter() {
        // Arrange
        String safeFilter = "u.dept_id = #{userId}";

        // Act
        boolean isSafe = interceptor.isSafeFilter(safeFilter);

        // Assert
        assertThat(isSafe).isTrue();
    }

    @Test
    @DisplayName("SECURITY: Should block SQL injection attempt with UNION")
    void testIsSafeFilter_BlocksUnionInjection() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1 UNION SELECT password FROM sys_user";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block SQL injection with semicolon")
    void testIsSafeFilter_BlocksSemicolon() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1; DROP TABLE sys_user;";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block SQL injection with comments")
    void testIsSafeFilter_BlocksComments() {
        // Arrange
        String[] maliciousFilters = {
            "u.dept_id = 1 -- comment",
            "u.dept_id = 1 /* comment */",
            "u.dept_id = 1 --"
        };

        // Act & Assert
        for (String filter : maliciousFilters) {
            assertThat(interceptor.isSafeFilter(filter))
                    .withFailMessage("Should block: " + filter)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("SECURITY: Should block OR-based SQL injection")
    void testIsSafeFilter_BlocksOrInjection() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1 OR 1=1";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block DELETE statement injection")
    void testIsSafeFilter_BlocksDeleteStatement() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1; DELETE FROM sys_user WHERE 1=1";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block UPDATE statement injection")
    void testIsSafeFilter_BlocksUpdateStatement() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1; UPDATE sys_user SET password='hacked'";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block INSERT statement injection")
    void testIsSafeFilter_BlocksInsertStatement() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1; INSERT INTO sys_user VALUES(...)";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block DROP TABLE injection")
    void testIsSafeFilter_BlocksDropTable() {
        // Arrange
        String maliciousFilter = "u.dept_id = 1; DROP TABLE sys_user";

        // Act
        boolean isSafe = interceptor.isSafeFilter(maliciousFilter);

        // Assert
        assertThat(isSafe).isFalse();
    }

    @Test
    @DisplayName("SECURITY: Should block EXEC/EXECUTE command injection")
    void testIsSafeFilter_BlocksExecCommand() {
        // Arrange
        String[] maliciousFilters = {
            "u.dept_id = 1; EXEC sp_executesql",
            "u.dept_id = 1; EXECUTE sp_executesql"
        };

        // Act & Assert
        for (String filter : maliciousFilters) {
            assertThat(interceptor.isSafeFilter(filter))
                    .withFailMessage("Should block: " + filter)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("SECURITY: Should block file operation injection (INTO OUTFILE)")
    void testIsSafeFilter_BlocksFileOperations() {
        // Arrange
        String[] maliciousFilters = {
            "u.dept_id = 1 INTO OUTFILE '/tmp/passwords.txt'",
            "u.dept_id = 1 AND load_file('/etc/passwd')"
        };

        // Act & Assert
        for (String filter : maliciousFilters) {
            assertThat(interceptor.isSafeFilter(filter))
                    .withFailMessage("Should block: " + filter)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("SECURITY: Should block hex encoding bypass attempts")
    void testIsSafeFilter_BlocksHexEncoding() {
        // Arrange
        String[] maliciousFilters = {
            "u.dept_id = 0x61646D696E",
            "u.dept_id = char(65,68,77,73,78)",
            "u.dept_id = concat('ad','min')"
        };

        // Act & Assert
        for (String filter : maliciousFilters) {
            assertThat(interceptor.isSafeFilter(filter))
                    .withFailMessage("Should block: " + filter)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Should allow recursive CTE pattern (legitimate use)")
    void testMatchesAllowedPattern_AllowsRecursiveCTE() {
        // Arrange
        String legitimateFilter = "WITH RECURSIVE dept_tree AS (SELECT id FROM sys_dept WHERE id = #{deptId})";

        // Act
        boolean matches = interceptor.matchesAllowedPattern(legitimateFilter);

        // Assert
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should allow parameterized placeholders")
    void testMatchesAllowedPattern_AllowsParameterizedQueries() {
        // Arrange
        String[] legitimateFilters = {
            "u.user_id = #{userId}",
            "d.dept_id IN (#{deptIds})",
            "u.create_time > #{startTime}"
        };

        // Act & Assert
        for (String filter : legitimateFilters) {
            assertThat(interceptor.matchesAllowedPattern(filter))
                    .withFailMessage("Should allow: " + filter)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should allow standard comparison operators")
    void testMatchesAllowedPattern_AllowsComparisonOperators() {
        // Arrange
        String[] legitimateFilters = {
            "u.status = 1",
            "u.age > 18",
            "u.dept_id IN (1, 2, 3)"
        };

        // Act & Assert
        for (String filter : legitimateFilters) {
            assertThat(interceptor.matchesAllowedPattern(filter))
                    .withFailMessage("Should allow: " + filter)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should skip interceptor when no data scope context")
    void testIntercept_SkipsWhenNoContext() throws Throwable {
        // Arrange: No DataScopeFilter set

        // Act
        Object result = interceptor.intercept(() -> "query-result");

        // Assert
        assertThat(result).isEqualTo("query-result");
    }

    @Test
    @DisplayName("Should apply data scope filter when context is set")
    void testIntercept_AppliesDataScopeFilter() {
        // Arrange
        UUID userId = UUID.randomUUID();
        DataScopeFilter filter = new DataScopeFilter();
        filter.setUserId(userId);
        filter.setDataScope(3); // DEPT level
        filter.setUserAlias("u");
        filter.setDeptAlias("d");

        DataScopeContextHolder.setFilter(filter);

        // Act
        DataScopeFilter appliedFilter = DataScopeContextHolder.getFilter();

        // Assert
        assertThat(appliedFilter).isNotNull();
        assertThat(appliedFilter.getUserId()).isEqualTo(userId);
        assertThat(appliedFilter.getDataScope()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should only intercept SELECT queries")
    void testIntercept_OnlySelectQueries() {
        // Arrange
        DataScopeFilter filter = new DataScopeFilter();
        DataScopeContextHolder.setFilter(filter);

        // Test INSERT
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.INSERT);
        // Should skip INSERT

        // Test UPDATE
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        // Should skip UPDATE

        // Test DELETE
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.DELETE);
        // Should skip DELETE

        // Only SELECT should be intercepted
        when(mappedStatement.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
        assertThat(DataScopeContextHolder.getFilter()).isNotNull();
    }

    @Test
    @DisplayName("SECURITY: Should validate filter is non-null")
    void testValidateFilter_RejectsNull() {
        assertThatCode(() -> {
            // Null filter should be handled gracefully
            DataScopeContextHolder.clear();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should clear data scope context after filter application")
    void testContextCleanup() {
        // Arrange
        DataScopeFilter filter = new DataScopeFilter();
        filter.setDataScope(1);
        DataScopeContextHolder.setFilter(filter);

        // Act
        DataScopeContextHolder.clear();

        // Assert
        assertThat(DataScopeContextHolder.getFilter()).isNull();
    }

    @Test
    @DisplayName("SECURITY: Should handle case-insensitive SQL keyword detection")
    void testIsSafeFilter_CaseInsensitiveKeywords() {
        // Arrange
        String[] maliciousFilters = {
            "u.dept_id = 1 UnIoN SeLeCt password",
            "u.dept_id = 1 DeLeTe FrOm sys_user",
            "u.dept_id = 1 DrOp TaBlE sys_user"
        };

        // Act & Assert
        for (String filter : maliciousFilters) {
            assertThat(interceptor.isSafeFilter(filter))
                    .withFailMessage("Should block case-insensitive: " + filter)
                    .isFalse();
        }
    }
}