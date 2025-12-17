package com.frog.common.data.rw.routing;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 读写路由上下文
 * <p>
 * 使用 ThreadLocal 保存当前线程的路由信息
 * 支持嵌套调用（使用栈结构）
 *
 * @author Deng
 * @since 2025-12-16
 */
@Slf4j
public class ReadWriteRoutingContext {
    /**
     * 路由类型栈（支持嵌套）
     */
    private static final ThreadLocal<Deque<RoutingType>> ROUTING_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * 最后写入时间（用于读写一致性保证）
     */
    private static final ThreadLocal<Instant> LAST_WRITE_TIME = new ThreadLocal<>();

    /**
     * 强制主库标记
     */
    private static final ThreadLocal<Boolean> FORCE_MASTER = ThreadLocal.withInitial(() -> false);

    /**
     * 指定的从库名称
     */
    private static final ThreadLocal<String> SPECIFIED_SLAVE = new ThreadLocal<>();

    /**
     * 路由类型
     */
    public enum RoutingType {
        /**
         * 主库
         */
        MASTER,

        /**
         * 从库
         */
        SLAVE,

        /**
         * 自动（根据事务和 SQL 类型判断）
         */
        AUTO
    }

    /**
     * 设置路由类型
     */
    public static void push(RoutingType type) {
        ROUTING_STACK.get().push(type);
        log.trace("[RW-Routing] Push routing type: {}", type);
    }

    /**
     * 弹出路由类型
     */
    public static void pop() {
        Deque<RoutingType> stack = ROUTING_STACK.get();
        if (!stack.isEmpty()) {
            RoutingType popped = stack.pop();
            log.trace("[RW-Routing] Pop routing type: {}", popped);
        }
    }

    /**
     * 获取当前路由类型
     */
    public static RoutingType current() {
        Deque<RoutingType> stack = ROUTING_STACK.get();
        return stack.isEmpty() ? RoutingType.AUTO : stack.peek();
    }

    /**
     * 设置强制主库
     */
    public static void forceMaster() {
        FORCE_MASTER.set(true);
        log.trace("[RW-Routing] Force master enabled");
    }

    /**
     * 清除强制主库
     */
    public static void clearForceMaster() {
        FORCE_MASTER.set(false);
        log.trace("[RW-Routing] Force master cleared");
    }

    /**
     * 是否强制主库
     */
    public static boolean isForceMaster() {
        return Boolean.TRUE.equals(FORCE_MASTER.get());
    }

    /**
     * 记录写操作时间（用于读写一致性）
     */
    public static void markWrite() {
        LAST_WRITE_TIME.set(Instant.now());
        log.trace("[RW-Routing] Write operation marked");
    }

    /**
     * 获取最后写入时间
     */
    public static Instant getLastWriteTime() {
        return LAST_WRITE_TIME.get();
    }

    /**
     * 指定从库
     */
    public static void specifySlave(String slaveName) {
        SPECIFIED_SLAVE.set(slaveName);
    }

    /**
     * 获取指定的从库
     */
    public static String getSpecifiedSlave() {
        return SPECIFIED_SLAVE.get();
    }

    /**
     * 清理上下文
     */
    public static void clear() {
        ROUTING_STACK.remove();
        LAST_WRITE_TIME.remove();
        FORCE_MASTER.remove();
        SPECIFIED_SLAVE.remove();
        log.trace("[RW-Routing] Context cleared");
    }

    /**
     * 判断是否应该走主库
     *
     * @param readMasterAfterWriteMs 写后读主库的时间窗口
     * @return true 如果应该走主库
     */
    public static boolean shouldUseMaster(long readMasterAfterWriteMs) {
        // 1. 强制主库
        if (isForceMaster()) {
            return true;
        }

        // 2. 显式指定主库
        if (current() == RoutingType.MASTER) {
            return true;
        }

        // 3. 写后读一致性检查
        Instant lastWrite = getLastWriteTime();
        if (lastWrite != null) {
            long elapsed = Instant.now().toEpochMilli() - lastWrite.toEpochMilli();
            if (elapsed < readMasterAfterWriteMs) {
                log.debug("[RW-Routing] Using master due to recent write ({}ms ago)", elapsed);
                return true;
            }
        }

        return false;
    }
}
