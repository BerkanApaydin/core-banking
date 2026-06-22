package com.bank.app.audit.infrastructure.decorator;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.domain.AuditAction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AuditLoggerTransactionDecorator implements AuditLoggerUseCase {

    private final AuditLoggerUseCase delegate;

    public AuditLoggerTransactionDecorator(AuditLoggerUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String details) {
        delegate.log(action, details);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, AuditAction action, String details) {
        delegate.log(username, action, details);
    }
}
