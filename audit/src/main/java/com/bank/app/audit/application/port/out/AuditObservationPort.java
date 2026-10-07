package com.bank.app.audit.application.port.out;

/**
 * Observability for the audit pipeline itself.
 *
 * <p>The audit row is persisted synchronously inside the business transaction
 * (see the {@code AuditEventPort} implementation); afterwards the same event
 * is dispatched as a Spring application event and observed here, after commit.
 * A steadily increasing consumed count proves the dispatch seam is wired —
 * a permanently-zero counter means the event never leaves the publisher.
 */
public interface AuditObservationPort {

    /**
     * An audit event completed the full pipeline (persist, then after-commit
     * dispatch). Never throws: observation must not break the caller.
     */
    void recordConsumed(String action);
}
