package com.bank.app.transfer.adapter.out.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.adapter.in.event.TransferEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TransferCancelledOutboxRelay extends AbstractTransferOutboxRelay {

    // NOTE: literal intentionally keeps the pre-rename handler name: dedup keys are
    // persisted by IdempotencyPort, so changing this value would reprocess old events.
    private static final String DEDUP_KEY_PREFIX = "outbox_handler_TransferCancelledOutboxHandler_";

    private final ObjectMapper objectMapper;
    private final TransferEventConsumer notificationConsumer;

    public TransferCancelledOutboxRelay(ObjectMapper objectMapper,
                                           TransferEventConsumer notificationConsumer,
                                           IdempotencyPort idempotencyPort,
                                           ClockProviderPort clockProvider) {
        super(idempotencyPort, clockProvider, "TransferCancelledEvent", DEDUP_KEY_PREFIX);
        this.objectMapper = objectMapper;
        this.notificationConsumer = notificationConsumer;
    }

    @Override
    protected void deliver(EventEntry event) throws Exception {
        TransferCancelledEvent cancelledEvent = objectMapper.readValue(
                event.payload(), TransferCancelledEvent.class);

        AsyncTransferCancelledEvent asyncEvent = new AsyncTransferCancelledEvent(
                cancelledEvent.transferId(),
                cancelledEvent.senderAccountId(),
                cancelledEvent.receiverAccountId(),
                cancelledEvent.amount(),
                cancelledEvent.status(),
                cancelledEvent.occurredAt());

        notificationConsumer.handleTransferCancelled(asyncEvent);
    }
}
