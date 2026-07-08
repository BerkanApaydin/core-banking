package com.bank.app.audit.adapter.in.event;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLoggerUseCase auditLoggerUseCase;

    public AuditEventListener(AuditLoggerUseCase auditLoggerUseCase) {
        this.auditLoggerUseCase = auditLoggerUseCase;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditAction action = AuditAction.fromString(event.action());
            auditLoggerUseCase.log(action, event.details());
        } catch (Exception e) {
            log.error("Failed to persist audit event: action={}, details={}", event.action(), event.details(), e);
        }
    }
}
