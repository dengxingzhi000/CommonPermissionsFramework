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

        // 获取SQL过滤条件
        String sqlFilter = DataScopeContextHolder.get();
        if (sqlFilter != null && !sqlFilter.isEmpty()) {
            BoundSql boundSql = statementHandler.getBoundSql();
            String originalSql = boundSql.getSql();

            // 在WHERE子句后添加数据权限过滤条件
            String newSql;
            if (originalSql.toLowerCase().contains("where")) {
                newSql = originalSql.replaceFirst("(?i)where", "WHERE " + sqlFilter + " AND ");
            } else {
                newSql = originalSql + " WHERE " + sqlFilter;
            }

            metaObject.setValue("delegate.boundSql.sql", newSql);
            log.debug("Data scope filter applied: {}", sqlFilter);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
