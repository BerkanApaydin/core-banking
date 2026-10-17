package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Async projection of a FAILED transfer for outbox delivery (mirrors
 * {@link AsyncTransferCompletedEvent} / {@link AsyncTransferCancelledEvent}).
 * Produced by {@code TransferFailedOutboxRelay} from the stored
 * {@link TransferFailedEvent}; consumed by {@code TransferEventConsumer} for
 * fan-out over {@code SendNotificationPort} channels.
 */
public record AsyncTransferFailedEvent(
    Long transferId,
    Long senderAccountId,
    Long receiverAccountId,
    Money amount,
    TransferStatus status,
    LocalDateTime occurredAt
) implements DomainEvent {
    public AsyncTransferFailedEvent {
        Objects.requireNonNull(transferId);
        Objects.requireNonNull(senderAccountId);
        Objects.requireNonNull(receiverAccountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(status);
        Objects.requireNonNull(occurredAt);
    }

    public static AsyncTransferFailedEvent from(Transfer transfer) {
        Objects.requireNonNull(transfer, "Transfer must not be null");
        // Legacy shape: failure time falls back to creation time. New code
        // should use from(transfer, failedAt) with the actual failure instant
        // (see TransferFailedEvent.occurredAt); the relay already does.
        return from(transfer, transfer.getCreatedAt());
    }

    /**
     * Preferred factory: explicit failure instant instead of the creation
     * timestamp (a failure reported days after placement must not carry the
     * placement time as its occurrence time).
     */
    public static AsyncTransferFailedEvent from(Transfer transfer, LocalDateTime failedAt) {
        Objects.requireNonNull(transfer, "Transfer must not be null");
        Objects.requireNonNull(failedAt, "Failure time must not be null");
        // Outbox JSON shape stays on Long; unwrap at this edge.
        return new AsyncTransferFailedEvent(
            transfer.getId(),
            transfer.getSenderAccountId().value(),
            transfer.getReceiverAccountId().value(),
            transfer.getAmount(),
            transfer.getStatus(),
            failedAt
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
