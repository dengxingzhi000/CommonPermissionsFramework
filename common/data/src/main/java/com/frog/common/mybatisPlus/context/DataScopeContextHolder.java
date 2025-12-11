package com.frog.common.mybatisPlus.context;

/**
 * 数据权限上下文
 *
 * @author Deng
 * createData 2025/10/15 14:31
 * @version 1.0
 */
public class DataScopeContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void set(String sqlFilter) {
        CONTEXT.set(sqlFilter);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
