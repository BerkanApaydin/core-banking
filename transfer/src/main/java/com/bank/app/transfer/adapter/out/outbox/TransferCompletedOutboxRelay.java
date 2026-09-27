package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.adapter.in.event.TransferEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TransferCompletedOutboxRelay extends AbstractTransferOutboxRelay {

    // NOTE: literal intentionally keeps the pre-rename handler name: dedup keys are
    // persisted by IdempotencyPort, so changing this value would reprocess old events.
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_TransferCompletedOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final TransferEventConsumer notificationConsumer;

    public TransferCompletedOutboxRelay(ObjectMapper objectMapper,
                                           TransferEventConsumer notificationConsumer,
                                           IdempotencyPort idempotencyPort) {
        super(idempotencyPort, "TransferCompletedEvent", DEDUP_KEY_PREFIX);
        this.objectMapper = objectMapper;
        this.notificationConsumer = notificationConsumer;
    }

    @Override
    protected void deliver(EventEntry event) throws Exception {
        TransferCompletedEvent completedEvent = objectMapper.readValue(
                event.payload(), TransferCompletedEvent.class);

        AsyncTransferCompletedEvent asyncEvent = new AsyncTransferCompletedEvent(
                completedEvent.transferId(),
                completedEvent.senderAccountId(),
                completedEvent.receiverAccountId(),
                completedEvent.amount(),
                completedEvent.status(),
                completedEvent.occurredAt());

        notificationConsumer.handleTransferCompleted(asyncEvent);
    }
}
