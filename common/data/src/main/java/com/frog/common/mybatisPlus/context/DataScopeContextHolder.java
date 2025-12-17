package com.frog.common.mybatisPlus.context;

/**
 * 数据权限上下文
 */
public class DataScopeContextHolder {

    private static final ThreadLocal<DataScopeFilter> CONTEXT = new ThreadLocal<>();

    public static void set(DataScopeFilter filter) {
        CONTEXT.set(filter);
    }

    public static DataScopeFilter get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
