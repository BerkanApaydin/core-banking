package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TransferCompletedOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(TransferCompletedOutboxHandler.class);
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_TransferCompletedOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyPort idempotencyPort;

    public TransferCompletedOutboxHandler(ObjectMapper objectMapper,
                                           ApplicationEventPublisher eventPublisher,
                                           IdempotencyPort idempotencyPort) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    public boolean supports(String eventType) {
        return "TransferCompletedEvent".equals(eventType);
    }

    @Override
    public void handle(EventEntry event) {
        String dedupKey = DEDUP_KEY_PREFIX + event.id();
        if (!idempotencyPort.tryCreate(dedupKey, LocalDateTime.now())) {
            log.info("Duplicate outbox event detected, skipping. handler=TransferCompletedOutboxHandler, eventId={}", event.id());
            return;
        }

        try {
            TransferCompletedEvent completedEvent = objectMapper.readValue(
                    event.payload(), TransferCompletedEvent.class);

            AsyncTransferCompletedEvent asyncEvent = new AsyncTransferCompletedEvent(
                    completedEvent.transferId(),
                    completedEvent.senderAccountId(),
                    completedEvent.receiverAccountId(),
                    completedEvent.amount(),
                    completedEvent.status(),
                    completedEvent.occurredAt());

            eventPublisher.publishEvent(asyncEvent);
            log.debug("Published AsyncTransferCompletedEvent for transfer id: {}",
                    completedEvent.transferId());
        } catch (Exception e) {
            log.error("Failed to handle TransferCompletedEvent: id={}", event.id(), e);
            throw new RuntimeException("TransferCompletedOutboxHandler failed", e);
        }
    }
}
