package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransferCompletedNotificationEvent(
    Long transferId,
    Long senderAccountId,
    Long receiverAccountId,
    Money amount,
    TransferStatus status,
    LocalDateTime occurredAt
) implements DomainEvent {
    public TransferCompletedNotificationEvent {
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(senderAccountId);
        Objects.requireNonNull(receiverAccountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(status);
        Objects.requireNonNull(occurredAt);
    }

    public static TransferCompletedNotificationEvent from(Transfer transfer) {
        // Outbox JSON shape stays on Long; unwrap at this edge.
        return new TransferCompletedNotificationEvent(
            transfer.getId(),
            transfer.getSenderAccountId().value(),
            transfer.getReceiverAccountId().value(),
            transfer.getAmount(),
            transfer.getStatus(),
            transfer.getCreatedAt()
        );
    }

    @Override
    public String aggregateType() {
        return "Transfer";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(transferId);
    }
}
