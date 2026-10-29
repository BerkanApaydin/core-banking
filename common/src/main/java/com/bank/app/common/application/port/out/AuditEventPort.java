package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.event.AuditEvent;

/**
 * Synchronous audit seam of the calling use case.
 *
 * <p>Transaction contract (R7): the production adapter
 * ({@code AuditEventPublisherAdapter}) persists the audit row through the
 * caller's active transaction. When the caller has no transaction (for
 * example a failed-login audit after authentication is rejected), the save
 * runs in the adapter's own transaction. A failed audit write therefore rolls
 * back money movement whenever a caller transaction exists, but it never
 * starts a REQUIRES_NEW transaction that could commit independently.
 *
 * <p>This is deliberately different from the AFTER_COMMIT observer dispatch
 * ({@code ApplicationEventPublisher}): the row is durable first, the event
 * second, so listeners never write twice.
 *
 * <p>Implementations must not start a new transaction. See
 * {@code UseCaseTransactionAspect}.
 */
public interface AuditEventPort {
    /**
     * Mandatory audit write. The caller treats a failure as fatal: a failed
     * audit write rolls back the surrounding transaction (money movement).
     */
    void publish(AuditEvent event);

    /**
     * Best-effort audit write for authentication lifecycle events
     * (docs/decisions/audit-atomicity.md mode 2). The production adapter
     * persists synchronously and, when that write fails, queues a durable
     * outbox retry instead of dropping the audit row. Implementations must not
     * fail the caller because authentication already succeeded/failed on its
     * own terms.
     */
    void publishBestEffort(AuditEvent event);
}
