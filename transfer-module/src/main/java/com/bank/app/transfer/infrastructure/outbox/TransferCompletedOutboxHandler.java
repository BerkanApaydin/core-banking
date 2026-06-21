package com.bank.app.transfer.infrastructure.outbox;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bank.app.common.outbox.OutboxEventHandler;
import com.bank.app.common.outbox.OutboxEventJpaEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TransferCompletedOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TransferCompletedOutboxHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean supports(String eventType) {
        return "TransferCompletedEvent".equals(eventType);
    }

    @Override
    public void handle(OutboxEventJpaEntity event) throws Exception {
        OutboxEventListener.TransferEventPayload payload = objectMapper.readValue(
                event.getPayload(),
                OutboxEventListener.TransferEventPayload.class);

        Transfer transfer = new Transfer(
                payload.transferId(),
                payload.senderAccountId(),
                payload.receiverAccountId(),
                new Money(payload.amount(), Currency.valueOf(payload.currency())),
                TransferStatus.COMPLETED,
                event.getCreatedAt());

        eventPublisher.publishEvent(AsyncTransferCompletedEvent.from(transfer));
    }
}
