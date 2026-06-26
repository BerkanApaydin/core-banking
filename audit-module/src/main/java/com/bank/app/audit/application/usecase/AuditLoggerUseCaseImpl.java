package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UseCase
public class AuditLoggerUseCaseImpl implements AuditLoggerUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerUseCaseImpl.class);

    private final SaveAuditLogPort saveAuditLogPort;
    private final SecurityContextPort securityContextPort;

    public AuditLoggerUseCaseImpl(SaveAuditLogPort saveAuditLogPort, SecurityContextPort securityContextPort) {
        this.saveAuditLogPort = saveAuditLogPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public void log(AuditAction action, String details) {
        String username = getCurrentUsername();
        AuditLog auditLog = AuditLog.create(username, action, details);
        saveAuditLogPort.save(auditLog);
        log.info("Audit log: user={}, action={}, details={}", username, action, details);
    }

    @Override
    public void log(String username, AuditAction action, String details) {
        AuditLog auditLog = AuditLog.create(username, action, details);
        saveAuditLogPort.save(auditLog);
        log.info("Audit log: user={}, action={}, details={}", username, action, details);
    }

    private String getCurrentUsername() {
        return securityContextPort.getCurrentUsername().orElse("system");
    }
}
