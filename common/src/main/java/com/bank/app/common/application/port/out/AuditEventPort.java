package com.bank.app.common.application.port.out;

import com.bank.app.common.domain.event.AuditEvent;

/**
 * Synchronous audit seam of the calling use case.
 *
 * <p>Transaction contract (R7): the production adapter
 * ({@code AuditEventPublisherAdapter}) persists the audit row <b>in the
 * caller's transaction</b> — a failed audit write rolls the money movement
 * back. This is deliberately different from the async audit use cases in
 * {@code audit.application.usecase} (REQUIRES_NEW, isolated) and from the
 * AFTER_COMMIT observer dispatch ({@code ApplicationEventPublisher}): the row
 * is durable first, the event second, so listeners never write twice.
 *
 * <p>Implementations must not start a new transaction (no REQUIRES_NEW here);
 * see {@code UseCaseTransactionAspect} — audit <i>use cases</i> are excluded
 * from the caller's transaction, audit <i>port publishes</i> join it.
 */
public interface AuditEventPort {
    void publish(AuditEvent event);
}
