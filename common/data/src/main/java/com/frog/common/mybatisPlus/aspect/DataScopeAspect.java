package com.frog.common.mybatisPlus.aspect;

import com.frog.common.mybatisPlus.annotation.DataScope;
import com.frog.common.mybatisPlus.context.DataScopeContextHolder;
import com.frog.common.mybatisPlus.context.DataScopeFilter;
import com.frog.common.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * 数据权限切面
 * 根据用户角色的dataScope自动注入SQL过滤条件
 *
 * <p>REFACTORED: Now depends on SecurityContext interface instead of concrete SecurityUser class.
 * This follows Dependency Inversion Principle (DIP) and decouples data layer from web layer.
 *
 * @author Deng
 * createData 2025/10/30 11:15
 * @version 2.0 - Refactored to use SecurityContext interface
 */
@Aspect
@Component
@Slf4j
public class DataScopeAspect {

    private final SecurityContext securityContext;

    /**
     * Constructor injection of SecurityContext.
     *
     * @param securityContext Security context interface (implementation provided by web layer)
     */
    public DataScopeAspect(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    /**
     * 拦截带有@DataScope注解的方法
     */
    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        try {
            // Check if user is authenticated via interface
            if (!securityContext.isAuthenticated()) {
                log.debug("User not authenticated, skipping data scope filtering");
                return point.proceed();
            }

            // Get user info via interface (no direct dependency on SecurityUser)
            UUID userId = securityContext.getCurrentUserId();
            UUID deptId = securityContext.getCurrentDeptId();
            Integer dataScopeLevel = securityContext.getDataScopeLevel();

            // Null check for userId (required for data scope)
            if (userId == null) {
                log.warn("Authenticated user has null userId, skipping data scope");
                return point.proceed();
            }

            // Build SQL filter based on data scope level
            DataScopeFilter filter = buildSqlFilter(dataScopeLevel, userId, deptId, dataScope);

            // 设置到ThreadLocal，由DataScopeInterceptor使用
            DataScopeContextHolder.set(filter);

            log.debug("Data scope applied: userId={}, level={}, filter={}",
                    userId, dataScopeLevel, filter.getClause());

            return point.proceed();
        } finally {
            // 清理ThreadLocal
            DataScopeContextHolder.clear();
        }
    }

    /**
     * Builds SQL filter clause for data scope.
     * Adapted for MySQL BINARY(16) UUID storage.
     *
     * SECURITY: Validates table aliases to prevent SQL injection through annotation parameters.
     */
    private DataScopeFilter buildSqlFilter(Integer dataScope, UUID userId, UUID deptId, DataScope annotation) {
        String deptAlias = validateSqlIdentifier(annotation.deptAlias(), "dept");
        String userAlias = validateSqlIdentifier(annotation.userAlias(), "user");

        return switch (dataScope) {
            case 1 -> // 全部数据权限
                    new DataScopeFilter("1=1", java.util.Collections.emptyMap());

            case 2 -> // 自定义数据权限（从数据库查询配置）
                    buildCustomDataScope(userId, deptAlias, userAlias);

            case 3 -> // 本部门数据权限
                    deptId != null
                            ? new DataScopeFilter(
                                    deptAlias + " = UNHEX(REPLACE(#{__ds_deptId}, '-', ''))",
                                    java.util.Map.of("__ds_deptId", deptId.toString()))
                            : new DataScopeFilter("1=0", java.util.Collections.emptyMap());

            case 4 -> // 本部门及以下数据权限
                    deptId != null
                            ? buildDeptAndChildrenScope(deptId, deptAlias)
                            : new DataScopeFilter("1=0", java.util.Collections.emptyMap());

            case 5 -> // 仅本人数据权限
                    new DataScopeFilter(
                            userAlias + " = UNHEX(REPLACE(#{__ds_userId}, '-', ''))",
                            java.util.Map.of("__ds_userId", userId.toString()));

            default ->
                    new DataScopeFilter("1=0", java.util.Collections.emptyMap()); // 无权限
        };
    }

    /**
     * 构建自定义数据权限
     */
    private DataScopeFilter buildCustomDataScope(UUID userId, String deptAlias, String userAlias) {
        // TODO: 从sys_role_data_permission表查询用户的自定义权限规则
        // 这里简化为仅本人
        return new DataScopeFilter(
                userAlias + " = UNHEX(REPLACE(#{__ds_userId}, '-', ''))",
                java.util.Map.of("__ds_userId", userId.toString())
        );
    }

    /**
     * 构建部门及子部门权限
     */
    private DataScopeFilter buildDeptAndChildrenScope(UUID deptId, String deptAlias) {
        // 使用递归CTE查询所有子部门
        String clause = """
                %s IN (WITH RECURSIVE dept_tree AS (
                  SELECT id FROM sys_dept WHERE id = UNHEX(REPLACE(#{__ds_deptId}, '-', ''))
                  UNION ALL
                  SELECT d.id FROM sys_dept d INNER JOIN dept_tree dt ON d.parent_id = dt.id
                ) SELECT HEX(id) FROM dept_tree)
                """.formatted(deptAlias);
        return new DataScopeFilter(clause, java.util.Map.of("__ds_deptId", deptId.toString()));
    }

    /**
     * Validates SQL identifier (table/column alias) to prevent SQL injection.
     * Only allows alphanumeric characters, underscore, and dot (for qualified names).
     *
     * @param identifier The identifier from @DataScope annotation
     * @param defaultValue Default value if identifier is invalid
     * @return Validated identifier or default
     * @throws IllegalArgumentException if identifier contains dangerous characters
     */
    private String validateSqlIdentifier(String identifier, String defaultValue) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return defaultValue;
        }

        // SQL identifiers should only contain: a-z, A-Z, 0-9, underscore, dot
        // Dot allows qualified names like "table.column"
        if (!identifier.matches("^[a-zA-Z0-9_.]+$")) {
            log.error("SECURITY: Invalid SQL identifier in @DataScope annotation: '{}'. " +
                     "Only alphanumeric, underscore, and dot allowed.", identifier);
            throw new IllegalArgumentException(
                "Invalid table/column alias in @DataScope: " + identifier);
        }

        // Additional safety: reject SQL keywords commonly used in attacks
        String lower = identifier.toLowerCase(Locale.ROOT);
        String[] forbiddenKeywords = {
            "select", "from", "where", "union", "insert", "update", "delete",
            "drop", "create", "alter", "exec", "execute", "or", "and"
        };

        for (String keyword : forbiddenKeywords) {
            if (lower.equals(keyword)) {
                log.error("SECURITY: SQL keyword used as identifier in @DataScope: '{}'", identifier);
                throw new IllegalArgumentException(
                    "SQL keyword cannot be used as alias in @DataScope: " + identifier);
            }
        }

        return identifier;
    }
}
