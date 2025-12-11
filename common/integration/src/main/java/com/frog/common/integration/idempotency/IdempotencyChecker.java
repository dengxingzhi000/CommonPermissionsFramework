package com.frog.common.integration.idempotency;

/**
 * Simple abstraction to prevent duplicate processing of the same message id.
 */
public interface IdempotencyChecker {

    /**
     * @param messageId unique id from envelope
     * @return true if this message id has not been processed and is now marked as processing; false if duplicate
     */
    boolean tryAcquire(String messageId);

    /**
     * Release a previously acquired id (optional for implementations).
     */
    default void release(String messageId) {
        // no-op
    }
}
