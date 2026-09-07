package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.UserContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLoggerUseCaseImpl implements AuditLoggerUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerUseCaseImpl.class);

    private final SaveAuditLogPort saveAuditLogPort;
    private final UserContextService userContextService;
    private final ClockProviderPort clockProvider;

    public AuditLoggerUseCaseImpl(SaveAuditLogPort saveAuditLogPort, UserContextService userContextService,
            ClockProviderPort clockProvider) {
        this.saveAuditLogPort = saveAuditLogPort;
        this.userContextService = userContextService;
        this.clockProvider = clockProvider;
    }

    @Override
    public void log(AuditAction action, String details) {
        String username = getCurrentUsername();
        AuditLog auditLog = AuditLog.create(username, action, details, clockProvider.clock());
        saveAuditLogPort.save(auditLog);
        log.info("Audit log: user={}, action={}, details={}", username, action, details);
    }

    @Override
    public void log(String username, AuditAction action, String details) {
        AuditLog auditLog = AuditLog.create(username, action, details, clockProvider.clock());
        saveAuditLogPort.save(auditLog);
        log.info("Audit log: user={}, action={}, details={}", username, action, details);
    }

    private String getCurrentUsername() {
        return userContextService.getCurrentUsername().orElse("system");
    }
}
