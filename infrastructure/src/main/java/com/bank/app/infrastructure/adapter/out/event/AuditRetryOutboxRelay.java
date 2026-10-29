package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.outbox.IdempotentOutboxRelay;
import com.bank.app.common.application.outbox.OutboxEventTypes;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Replays best-effort audit rows that could not be written synchronously.
 *
 * <p>The outbox row is the durability boundary; this handler inserts the
 * missing audit row and then dispatches the same AFTER_COMMIT observation
 * event as the synchronous adapter. Duplicate delivery is suppressed by the
 * shared outbox handler idempotency key.
 */
@Component
public class AuditRetryOutboxRelay extends IdempotentOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(AuditRetryOutboxRelay.class);

    static final String DEDUP_KEY_PREFIX = "outbox_handler_AuditRetryOutboxRelay_";

    private final ObjectMapper objectMapper;
    private final SaveAuditLogPort saveAuditLogPort;
    private final ApplicationEventPublisher eventPublisher;

    public AuditRetryOutboxRelay(ObjectMapper objectMapper,
            SaveAuditLogPort saveAuditLogPort,
            ApplicationEventPublisher eventPublisher,
            IdempotencyPort idempotencyPort,
            ClockProviderPort clockProvider) {
        super(idempotencyPort, clockProvider, DEDUP_KEY_PREFIX);
        this.objectMapper = objectMapper;
        this.saveAuditLogPort = saveAuditLogPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String eventType) {
        return OutboxEventTypes.matches("AuditEvent", eventType);
    }

    @Override
    protected void deliver(OutboxPort.EventEntry event) throws Exception {
        AuditEvent auditEvent = objectMapper.readValue(event.payload(), AuditEvent.class);
        saveAuditLogPort.save(AuditLog.create(auditEvent.username(),
                AuditAction.fromString(auditEvent.action()), auditEvent.details(),
                auditEvent.occurredAt(), auditEvent.actorUserId()));
        eventPublisher.publishEvent(auditEvent);
        log.info("Deferred audit event persisted: id={}, action={}", event.id(), auditEvent.action());
    }

    @Override
    protected void onDuplicateSkipped(OutboxPort.EventEntry event) {
        log.info("Duplicate deferred audit event detected, skipping. eventId={}", event.id());
    }

    @Override
    protected void onDeliveryFailed(OutboxPort.EventEntry event, Exception failure) {
        log.error("Failed to persist deferred audit event: id={}, failureType={}",
                event.id(), failure.getClass().getName());
        super.onDeliveryFailed(event, failure);
    }
}
