package com.bank.app.audit.application.port;

import com.bank.app.audit.domain.AuditLog;

public interface SaveAuditLogPort {
    AuditLog save(AuditLog auditLog);
}
