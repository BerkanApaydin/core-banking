package com.bank.app.audit.application.port.out;

/**
 * Records audit persistence failures for observability. Audit logging must never break
 * the business transaction, so failures are swallowed by design — this port makes them
 * visible to monitoring instead of disappearing into a log line.
 */
public interface AuditFailurePort {

    void recordFailure(String action, String reason);
}
