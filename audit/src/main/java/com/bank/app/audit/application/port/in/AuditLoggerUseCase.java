package com.bank.app.audit.application.port.in;

import com.bank.app.audit.domain.AuditAction;

/**
 * The audit module's public write API.
 *
 * <p>Currently no production flow calls it: use cases persist through
 * {@code AuditEventPort} (synchronous, inside the business transaction) and
 * the after-commit listener only observes. Kept as the module's published
 * entry point for future direct writers.
 */
public interface AuditLoggerUseCase {
    void log(AuditAction action, String details);
    void log(String username, AuditAction action, String details);
}
