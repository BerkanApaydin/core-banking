package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TransferCancelledOutboxHandler implements OutboxEventPort {

    private static final Logger log = LoggerFactory.getLogger(TransferCancelledOutboxHandler.class);

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TransferCancelledOutboxHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String eventType) {
        return "TransferCancelledEvent".equals(eventType);
    }

    @Override
    public void handle(EventEntry event) {
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
