package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuditEventPublisherAdapter implements AuditEventPort {

    private final SaveAuditLogPort saveAuditLogPort;
    private final ApplicationEventPublisher eventPublisher;

    public AuditEventPublisherAdapter(SaveAuditLogPort saveAuditLogPort,
            ApplicationEventPublisher eventPublisher) {
        this.saveAuditLogPort = saveAuditLogPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(AuditEvent event) {
        // Mandatory audit persistence joins the caller's transaction when one
        // is active; a failed audit write must roll back a successful money
        // movement. Without a caller transaction it uses the adapter's own.
        saveAuditLogPort.save(AuditLog.create(event.username(),
                AuditAction.fromString(event.action()), event.details(), event.occurredAt(),
                event.actorUserId()));
        // ...then dispatch for AFTER_COMMIT observation (see AuditEventConsumer):
        // the consumed counter proves this seam is wired end-to-end. The row
        // itself is already durable, so the listener never writes twice.
        eventPublisher.publishEvent(event);
    }
}
