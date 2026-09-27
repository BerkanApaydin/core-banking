package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuditEventPublisherAdapter implements AuditEventPort {

    private final SaveAuditLogPort saveAuditLogPort;

    public AuditEventPublisherAdapter(SaveAuditLogPort saveAuditLogPort) {
        this.saveAuditLogPort = saveAuditLogPort;
    }

    @Override
    public void publish(AuditEvent event) {
        // Mandatory audit persistence participates in the caller's transaction.
        // A failed audit write must roll back a successful money movement.
        saveAuditLogPort.save(new AuditLog(null, event.username(),
                AuditAction.fromString(event.action()), event.details(), event.occurredAt()));
    }
}
