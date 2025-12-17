package com.frog.common.data.rw.sql;

import com.frog.common.data.rw.routing.ReadWriteRoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * MyBatis SQL 路由拦截器
 * <p>
 * 参考：
 * - 美团 Zebra ZebraInterceptor
 * - Apache ShardingSphere SQLRouteExecutor
 * <p>
 * 在 SQL 执行前解析 SQL 类型和 Hint，设置路由上下文
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlRoutingInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 如果已经有显式路由设置，不再处理
        if (ReadWriteRoutingContext.current() != ReadWriteRoutingContext.RoutingType.AUTO) {
            return invocation.proceed();
        }

        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        // 获取 SQL
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();

        // 1. 解析 Hint
        SqlTypeParser.RoutingHint hint = SqlTypeParser.parseHint(sql);
        if (hint.type() != SqlTypeParser.RoutingHint.HintType.NONE) {
            return executeWithHint(invocation, hint);
        }

        // 2. 根据 MyBatis SqlCommandType 判断
        SqlCommandType commandType = ms.getSqlCommandType();
        if (commandType == SqlCommandType.SELECT) {
            // 进一步检查是否有 FOR UPDATE
            SqlTypeParser.SqlType sqlType = SqlTypeParser.parse(sql);
            if (sqlType == SqlTypeParser.SqlType.WRITE) {
                // SELECT ... FOR UPDATE，走主库
                log.debug("[SQL-Routing] Detected SELECT FOR UPDATE, routing to MASTER");
                return executeWithMaster(invocation);
            }

            // 普通 SELECT，走从库
            log.debug("[SQL-Routing] Detected SELECT, routing to SLAVE");
            return executeWithSlave(invocation);
        }

        // INSERT/UPDATE/DELETE，走主库并标记写操作
        log.debug("[SQL-Routing] Detected {} operation, routing to MASTER", commandType);
        return executeWithMasterAndMarkWrite(invocation);
    }

    private Object executeWithHint(Invocation invocation, SqlTypeParser.RoutingHint hint)
            throws Throwable {
        switch (hint.type()) {
            case MASTER -> {
                log.debug("[SQL-Routing] Hint: MASTER");
                return executeWithMaster(invocation);
            }
            case SLAVE -> {
                if (hint.slaveName() != null) {
                    log.debug("[SQL-Routing] Hint: SLAVE({})", hint.slaveName());
                    ReadWriteRoutingContext.specifySlave(hint.slaveName());
                } else {
                    log.debug("[SQL-Routing] Hint: SLAVE");
                }
                return executeWithSlave(invocation);
            }
            default -> {
                return invocation.proceed();
            }
        }
    }

    private Object executeWithMaster(Invocation invocation) throws Throwable {
        ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.MASTER);
        try {
            return invocation.proceed();
        } finally {
            ReadWriteRoutingContext.pop();
        }
    }

    private Object executeWithMasterAndMarkWrite(Invocation invocation) throws Throwable {
        ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.MASTER);
        try {
            Object result = invocation.proceed();
            // 写操作成功后标记
            ReadWriteRoutingContext.markWrite();
            return result;
        } finally {
            ReadWriteRoutingContext.pop();
        }
    }

    private Object executeWithSlave(Invocation invocation) throws Throwable {
        ReadWriteRoutingContext.push(ReadWriteRoutingContext.RoutingType.SLAVE);
        try {
            return invocation.proceed();
        } finally {
            ReadWriteRoutingContext.pop();
            ReadWriteRoutingContext.specifySlave(null); // 清除指定的从库
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
