package com.bank.app.audit.application.port.out;

import java.time.LocalDateTime;

/**
 * Retention boundary for the audit trail. Implemented by the audit
 * persistence adapter, driven by {@code AuditRetentionScheduler} in
 * infrastructure (same split as the outbox retention job).
 */
public interface AuditRetentionPort {

    /**
     * Deletes audit rows strictly older than the cutoff.
     *
     * @return deleted row count
     */
    int deleteOlderThan(LocalDateTime cutoff);
}
