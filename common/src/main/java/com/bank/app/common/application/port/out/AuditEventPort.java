package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.event.AuditEvent;

/**
 * Audit seam of the calling use case.
 *
 * <p>Transaction contract (R7): {@link #publish} is mandatory and the
 * production adapter ({@code AuditEventPublisherAdapter}) persists the audit
 * row through the caller's active transaction; a failed write therefore rolls
 * back money movement whenever a caller transaction exists. Without a caller
 * transaction the save runs in the adapter's own transaction.
 *
 * <p>{@link #publishBestEffort} is the authentication-lifecycle exception.
 * When a caller transaction is active the adapter defers the write until that
 * transaction completes and submits it to a dedicated single-thread executor,
 * so the caller never waits for a second connection; a synchronous
 * {@code REQUIRES_NEW} write while the caller still holds a connection can
 * deadlock a small pool under concurrency. Without a caller
 * transaction the write runs synchronously in {@code REQUIRES_NEW}. A failed
 * write queues a durable outbox retry. Executor queue rejection is the
 * remaining loss path: it is visible through
 * {@code audit.best_effort.rejected} and never fails the caller. See
 * {@code docs/decisions/audit-atomicity.md} mode 2 for the residual trade-off.
 *
 * <p>This is deliberately different from the AFTER_COMMIT observer dispatch
 * ({@code ApplicationEventPublisher}): the row (or its retry ticket) is durable
 * first, the event second, so listeners never write twice.
 */
public interface AuditEventPort {
    /**
     * Mandatory audit write. The caller treats a failure as fatal: a failed
     * audit write rolls back the surrounding transaction (money movement).
     */
    void publish(AuditEvent event);

    /**
     * Best-effort audit write for authentication lifecycle events
     * (docs/decisions/audit-atomicity.md mode 2). With an active caller
     * transaction the production adapter defers the write until completion and
     * runs it on a dedicated executor in its own transaction; without one it
     * writes synchronously in its own transaction. A failed write queues a
     * durable outbox retry; executor queue rejection loses the row but is
     * metered. Implementations must not fail the caller because authentication
     * already succeeded/failed on its own terms.
     *
     * <p>The independent transaction means the row may survive a later caller
     * rollback. That is acceptable for authentication attempts: a visible
     * audit attempt is preferred over losing the trace, and the row can never
     * turn a successful auth action into a failure.
     */
    void publishBestEffort(AuditEvent event);
}
