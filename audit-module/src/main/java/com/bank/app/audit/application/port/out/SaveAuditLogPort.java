package com.bank.app.audit.application.port.out;

import com.bank.app.audit.domain.AuditLog;

public interface SaveAuditLogPort {
    AuditLog save(AuditLog auditLog);
}
