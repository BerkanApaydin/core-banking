package com.bank.app.user.adapter.out.outbox;

import com.bank.app.common.application.outbox.IdempotentOutboxRelay;
import com.bank.app.common.application.outbox.OutboxEventTypes;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.user.domain.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Relay for {@code UserRegisteredEvent}.
 *
 * <p>Audit DRY follow-up: this relay hand-rolled the claim-then-deliver dedup
 * instead of inheriting it, duplicating {@link IdempotentOutboxRelay} line by
 * line (minus the key lifecycle). It now extends the shared skeleton and only
 * owns its event type, payload mapping and log lines — including the delivery
 * lifecycle fix (markCompleted/markFailed + FAILED reopen) for free.
 */
@Component
public class UserEventOutboxRelay extends IdempotentOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(UserEventOutboxRelay.class);
    // NOTE: literal intentionally keeps the pre-rename handler name: dedup keys are
    // persisted by IdempotencyPort, so changing this value would reprocess old events.
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_UserEventOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public UserEventOutboxRelay(ObjectMapper objectMapper,
                                   ApplicationEventPublisher eventPublisher,
                                   IdempotencyPort idempotencyPort,
                                   ClockProviderPort clockProvider) {
        super(idempotencyPort, clockProvider, DEDUP_KEY_PREFIX);
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String eventType) {
        return OutboxEventTypes.matches("UserRegisteredEvent", eventType);
    }

    @Override
    protected void deliver(EventEntry event) throws Exception {
        UserRegisteredEvent userEvent = objectMapper.readValue(
                event.payload(), UserRegisteredEvent.class);
        eventPublisher.publishEvent(userEvent);
        log.debug("Published UserRegisteredEvent: id={}", event.id());
    }

    @Override
    protected void onDuplicateSkipped(EventEntry event) {
        log.info("Duplicate outbox event detected, skipping. handler=UserEventOutboxRelay, eventId={}", event.id());
    }

    @Override
    protected void onDeliveryFailed(EventEntry event, Exception failure) {
        log.error("Failed to handle UserRegisteredEvent: id={}, failureType={}",
                event.id(), failure.getClass().getName());
        super.onDeliveryFailed(event, failure);
    }
}
