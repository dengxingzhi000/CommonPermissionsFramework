package com.frog.common.redis.lock;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-closeable handle returned to callers for deterministic unlock.
 */
public final class LockHandle implements AutoCloseable {
    private final DistributedLock lock;
    private final LockToken token;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LockHandle(DistributedLock lock, LockToken token) {
        this.lock = lock;
        this.token = token;
    }

    public LockToken token() {
        return token;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            lock.releaseInternal(token);
        }
    }
}

