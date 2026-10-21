package com.bank.app.infrastructure.adapter.in.outbox;

/**
 * Stable failure type recorded when the batch deadline expires before an
 * outbox worker could finish (or even start) an event. Persisting a dedicated
 * type keeps timeout-driven retries distinguishable from handler failures in
 * {@code last_error} and metrics.
 */
final class OutboxWorkerTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    OutboxWorkerTimeoutException(String message) {
        super(message);
    }
}
