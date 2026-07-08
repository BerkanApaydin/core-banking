package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TransferCancelledOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(TransferCancelledOutboxHandler.class);
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_TransferCancelledOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyPort idempotencyPort;

    public TransferCancelledOutboxHandler(ObjectMapper objectMapper,
                                           ApplicationEventPublisher eventPublisher,
                                           IdempotencyPort idempotencyPort) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.idempotencyPort = idempotencyPort;
    }

    @Override
    public boolean supports(String eventType) {
        return "TransferCancelledEvent".equals(eventType);
    }

    @Override
    public void handle(EventEntry event) {
        String dedupKey = DEDUP_KEY_PREFIX + event.id();
        if (!idempotencyPort.tryCreate(dedupKey, LocalDateTime.now())) {
            log.info("Duplicate outbox event detected, skipping. handler=TransferCancelledOutboxHandler, eventId={}", event.id());
            return;
        }

        try {
            TransferCancelledEvent cancelledEvent = objectMapper.readValue(
                    event.payload(), TransferCancelledEvent.class);

            AsyncTransferCancelledEvent asyncEvent = new AsyncTransferCancelledEvent(
                    cancelledEvent.transferId(),
                    cancelledEvent.senderAccountId(),
                    cancelledEvent.receiverAccountId(),
                    cancelledEvent.amount(),
                    cancelledEvent.status(),
                    cancelledEvent.occurredAt());

            eventPublisher.publishEvent(asyncEvent);
            log.debug("Published AsyncTransferCancelledEvent for transfer id: {}",
                    cancelledEvent.transferId());
        } catch (Exception e) {
            log.error("Failed to handle TransferCancelledEvent: id={}", event.id(), e);
            throw new RuntimeException("TransferCancelledOutboxHandler failed", e);
        }
    }
}
