package com.bank.app.audit.application;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AuditLogger implements AuditLoggerUseCase {

    private final SaveAuditLogPort saveAuditLogPort;
    private final SecurityContextPort securityContextPort;

    public AuditLogger(SaveAuditLogPort saveAuditLogPort, SecurityContextPort securityContextPort) {
        this.saveAuditLogPort = saveAuditLogPort;
        this.securityContextPort = securityContextPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String details) {
        String username = getCurrentUsername();
        log(username, action, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, AuditAction action, String details) {
        AuditLog auditLog = AuditLog.create(username, action, details);
        saveAuditLogPort.save(auditLog);
    }

    private String getCurrentUsername() {
        return securityContextPort.getCurrentUsername().orElse("system");
    }
}
