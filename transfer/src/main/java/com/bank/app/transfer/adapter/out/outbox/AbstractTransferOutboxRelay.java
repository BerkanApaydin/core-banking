package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

abstract class AbstractTransferOutboxRelay implements OutboxEventPort {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final IdempotencyPort idempotencyPort;
    private final String eventType;
    private final String dedupKeyPrefix;

    protected AbstractTransferOutboxRelay(IdempotencyPort idempotencyPort,
                                          String eventType,
                                          String dedupKeyPrefix) {
        this.idempotencyPort = idempotencyPort;
        this.eventType = eventType;
        this.dedupKeyPrefix = dedupKeyPrefix;
    }

    @Override
    public final boolean supports(String candidate) {
        return eventType.equals(candidate);
    }

    @Override
    public final void handle(EventEntry event) {
        if (!idempotencyPort.tryCreate(dedupKeyPrefix + event.id(), LocalDateTime.now())) {
            log.info("Duplicate outbox event detected, skipping. handler={}, eventId={}",
                    getClass().getSimpleName(), event.id());
            return;
        }

        try {
            deliver(event);
        } catch (Exception e) {
            log.error("Failed to handle {}: id={}, failureType={}",
                    eventType, event.id(), e.getClass().getName());
            throw new RuntimeException(getClass().getSimpleName() + " failed", e);
        }
    }

    protected abstract void deliver(EventEntry event) throws Exception;
}
