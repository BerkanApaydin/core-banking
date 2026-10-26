package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.TransferFailedNotificationEvent;
import com.bank.app.transfer.domain.TransferFailedEvent;
import com.bank.app.transfer.adapter.in.event.TransferEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Audit follow-up: FAILED transfers previously had no outbox relay, so a
 * FAILED transfer was visible only in the audit trail — downstream consumers
 * (notifications, future saga participants) never learned about it. This relay
 * closes that gap with the same at-least-once + dedup contract as the
 * COMPLETED/CANCELLED relays. It stays dormant until the documented async
 * placement/reconciliation flow starts producing FAILED transfers.
 */
@Component
public class TransferFailedOutboxRelay extends AbstractTransferOutboxRelay {

    private static final String DEDUP_KEY_PREFIX = "outbox_handler_TransferFailedOutboxRelay_";

    private final ObjectMapper objectMapper;
    private final TransferEventConsumer notificationConsumer;

    public TransferFailedOutboxRelay(ObjectMapper objectMapper,
                                           TransferEventConsumer notificationConsumer,
                                           IdempotencyPort idempotencyPort,
                                           ClockProviderPort clockProvider) {
        super(idempotencyPort, clockProvider, "TransferFailedEvent", DEDUP_KEY_PREFIX);
        this.objectMapper = objectMapper;
        this.notificationConsumer = notificationConsumer;
    }

    @Override
    protected void deliver(EventEntry event) throws Exception {
        TransferFailedEvent failedEvent = objectMapper.readValue(
                event.payload(), TransferFailedEvent.class);

        TransferFailedNotificationEvent notificationEvent = new TransferFailedNotificationEvent(
                failedEvent.transferId(),
                failedEvent.senderAccountId(),
                failedEvent.receiverAccountId(),
                failedEvent.amount(),
                failedEvent.status(),
                failedEvent.occurredAt());

        notificationConsumer.handleTransferFailed(notificationEvent);
    }
}
