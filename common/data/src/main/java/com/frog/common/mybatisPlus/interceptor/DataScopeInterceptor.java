package com.frog.common.mybatisPlus.interceptor;

import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Locale;
import java.util.Properties;

/**
 * MyBatis拦截器 - 自动添加数据权限过滤
 *
 * @author Deng
 * createData 2025/10/15 14:32
 * @version 1.0
 */
@Slf4j
@Component
@Intercepts(
        {@Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}
        )}
)
public class DataScopeInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        // 获取 SQL过滤条件
        var filter = DataScopeContextHolder.get();
        if (filter != null && filter.getClause() != null && !filter.getClause().isEmpty()
                && isSafeFilter(filter.getClause()) && isSelectStatement(metaObject)) {
            BoundSql boundSql = statementHandler.getBoundSql();
            String originalSql = boundSql.getSql();

            String newSql = appendFilter(originalSql, filter.getClause());

            metaObject.setValue("delegate.boundSql.sql", newSql);
            // 追加参数以支持 #{...} 占位符
            filter.getParams().forEach(boundSql::setAdditionalParameter);

            log.debug("Data scope filter applied: {}", filter.getClause());
        }

        return invocation.proceed();
    }

    /**
     * Enhanced SQL injection prevention.
     * Validates that the filter clause only contains safe SQL patterns.
     *
     * @param filter The data scope filter clause
     * @return true if filter is safe, false otherwise
     */
    private boolean isSafeFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            return false;
        }

        String lower = filter.toLowerCase(Locale.ROOT);

        // Block dangerous SQL keywords and patterns
        String[] dangerousPatterns = {
            ";",              // Statement terminator
            "--",             // SQL comment
            "/*",             // Multi-line comment start
            "*/",             // Multi-line comment end
            " union ",        // Union injection
            " union(",        // Union injection variant
            " or ",           // OR injection (can bypass conditions)
            " or(",           // OR injection variant
            "exec ",          // Execute command
            "execute ",       // Execute command
            "delete ",        // Delete statement
            "update ",        // Update statement
            "insert ",        // Insert statement
            "drop ",          // Drop statement
            "create ",        // Create statement
            "alter ",         // Alter statement
            "truncate ",      // Truncate statement
            "into outfile",   // File export
            "into dumpfile",  // File export
            "load_file",      // File read
            "xp_",            // SQL Server extended procedures
            "sp_",            // SQL Server stored procedures
            "0x",             // Hex encoding (bypass attempts)
            "char(",          // Character encoding bypass
            "concat(",        // String concat (can be used for injection)
        };

        for (String pattern : dangerousPatterns) {
            if (lower.contains(pattern)) {
                log.warn("SECURITY: Rejected data scope filter containing dangerous pattern '{}': {}",
                         pattern, filter);
                return false;
            }
        }

        // Whitelist: Only allow expected patterns for data scope
        // Valid patterns: column = #{param}, column IN (...), WITH RECURSIVE ..., AND, parentheses
        if (!matchesAllowedPattern(filter)) {
            log.warn("SECURITY: Rejected data scope filter with unexpected pattern: {}", filter);
            return false;
        }

        return true;
    }

    /**
     * Validates that filter matches expected data scope patterns.
     * Only allows: equality, IN clause, CTEs, and boolean operators.
     */
    private boolean matchesAllowedPattern(String filter) {
        String lower = filter.toLowerCase(Locale.ROOT);

        // Allow simple cases
        if (lower.equals("1=1") || lower.equals("1=0")) {
            return true;
        }

        // Must contain either:
        // 1. Parameterized placeholder: #{...}
        // 2. Recursive CTE for department hierarchy: WITH RECURSIVE
        boolean hasParameter = filter.contains("#{");
        boolean hasRecursiveCTE = lower.contains("with recursive");

        if (!hasParameter && !hasRecursiveCTE) {
            return false;
        }

        // Additional validation: should contain standard SQL comparison operators
        boolean hasComparison = lower.contains("=") ||
                               lower.contains(" in ") ||
                               lower.contains(" in(");

        return hasComparison || hasRecursiveCTE;
    }

    private boolean isSelectStatement(MetaObject metaObject) {
        try {
            Object cmd = metaObject.getValue("delegate.mappedStatement.sqlCommandType");
            return cmd != null && "SELECT".equalsIgnoreCase(cmd.toString());
        } catch (Exception e) {
            // On error, default to false for security (don't modify unknown SQL types)
            log.debug("Could not determine SQL command type, skipping data scope filter", e);
            return false;
        }
    }

    /**
     * Appends data scope filter to SQL WHERE clause.
     * NOTE: This still uses string manipulation, but the filter is heavily validated.
     *
     * IMPORTANT: The actual parameter values are passed separately via BoundSql.setAdditionalParameter(),
     * so they are properly escaped by JDBC PreparedStatement. Only the clause structure is concatenated.
     *
     * @param originalSql The original SQL query
     * @param filter The validated filter clause (contains #{...} placeholders, not actual values)
     * @return Modified SQL with filter applied
     */
    private String appendFilter(String originalSql, String filter) {
        String lower = originalSql.toLowerCase(Locale.ROOT);
        int whereIdx = lower.indexOf(" where ");

        if (whereIdx >= 0) {
            // Insert filter right after WHERE keyword
            return originalSql.substring(0, whereIdx + 7) +
                   "(" + filter + ") AND " +
                   originalSql.substring(whereIdx + 7);
        } else {
            // No WHERE clause, add one
            return originalSql + " WHERE (" + filter + ")";
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
