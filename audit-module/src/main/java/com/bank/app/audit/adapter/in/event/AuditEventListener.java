package com.bank.app.audit.adapter.in.event;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.event.AuditEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventListener {

    private final AuditLoggerUseCase auditLoggerUseCase;

    public AuditEventListener(AuditLoggerUseCase auditLoggerUseCase) {
        this.auditLoggerUseCase = auditLoggerUseCase;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditEvent event) {
        AuditAction action = AuditAction.fromString(event.action());
        auditLoggerUseCase.log(action, event.details());
    }
}
