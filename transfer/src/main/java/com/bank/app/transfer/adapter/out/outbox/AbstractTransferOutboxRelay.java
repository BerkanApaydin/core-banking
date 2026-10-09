package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.outbox.IdempotentOutboxRelay;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractTransferOutboxRelay extends IdempotentOutboxRelay {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String eventType;

    protected AbstractTransferOutboxRelay(IdempotencyPort idempotencyPort,
                                          ClockProviderPort clockProvider,
                                          String eventType,
                                          String dedupKeyPrefix) {
        super(idempotencyPort, clockProvider, dedupKeyPrefix);
        this.eventType = eventType;
    }

    @Override
    public final boolean supports(String candidate) {
        return eventType.equals(candidate);
    }

    @Override
    protected void onDuplicateSkipped(EventEntry event) {
        log.info("Duplicate outbox event detected, skipping. handler={}, eventId={}",
                getClass().getSimpleName(), event.id());
    }

    @Override
    protected void onDeliveryFailed(EventEntry event, Exception failure) {
        log.error("Failed to handle {}: id={}, failureType={}",
                eventType, event.id(), failure.getClass().getName());
        super.onDeliveryFailed(event, failure);
    }
}
