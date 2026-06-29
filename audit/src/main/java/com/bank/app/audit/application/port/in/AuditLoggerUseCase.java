package com.bank.app.audit.application.port.in;

import com.bank.app.audit.domain.AuditAction;

public interface AuditLoggerUseCase {
    void log(AuditAction action, String details);
    void log(String username, AuditAction action, String details);
}
