package com.bank.app.transfer.domain;

import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import com.bank.app.transfer.domain.exception.TransferNotPendingException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transfer extends BaseAggregateRoot {

    private static final Clock DEFAULT_CLOCK = Clock.systemDefaultZone();
    private final Long id;
    private final Long senderAccountId;
    private final Long receiverAccountId;
    private final Money amount;
    private TransferStatus status;
    private final LocalDateTime createdAt;
    private final Long version;

    public Transfer(Long id, Long senderAccountId, Long receiverAccountId, Money amount, TransferStatus status, LocalDateTime createdAt) {
        this(id, senderAccountId, receiverAccountId, amount, status, createdAt, null);
    }

    public Transfer(Long id, Long senderAccountId, Long receiverAccountId, Money amount, TransferStatus status, LocalDateTime createdAt, Long version) {
        this.id = id;
        this.senderAccountId = Objects.requireNonNull(senderAccountId, "Sender account ID must not be null");
        this.receiverAccountId = Objects.requireNonNull(receiverAccountId, "Receiver account ID must not be null");
        this.amount = Objects.requireNonNull(amount, "Transfer amount must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created date must not be null");
        this.version = version;
    }

    public static Transfer create(Long senderAccountId, Long receiverAccountId, Money amount) {
        return create(senderAccountId, receiverAccountId, amount, DEFAULT_CLOCK);
    }

    public static Transfer create(Long senderAccountId, Long receiverAccountId, Money amount, Clock clock) {
        Objects.requireNonNull(amount, "Transfer amount must not be null");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Transfer amount must not be zero");
        }
        return new Transfer(null, senderAccountId, receiverAccountId, amount, TransferStatus.PENDING, LocalDateTime.now(clock));
    }

    public void complete() {
        complete(DEFAULT_CLOCK);
    }

    public void complete(Clock clock) {
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status);
        }
        this.status = TransferStatus.COMPLETED;
        registerEvent(new TransferCompletedEvent(
                this.id, this.senderAccountId, this.receiverAccountId, this.amount, this.status, LocalDateTime.now(clock)));
    }

    public Long getId() {
        return id;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public Money getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transfer other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Transfer{id=" + id + ", sender=" + senderAccountId + ", receiver=" + receiverAccountId
                + ", amount=" + amount + ", status=" + status + "}";
    }

    public void markFailed() {
        markFailed(DEFAULT_CLOCK);
    }

    public void markFailed(Clock clock) {
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status);
        }
        this.status = TransferStatus.FAILED;
    }

    public void cancel(Clock clock, int cancellationWindowHours) {
        if (this.status == TransferStatus.CANCELLED) {
            throw new TransferAlreadyCancelledException(this.id);
        }
        if (this.status != TransferStatus.COMPLETED) {
            String detail = switch (this.status) {
                case PENDING -> "Transfer is still pending and cannot be cancelled. Cancel is only available for completed transfers.";
                case FAILED -> "Transfer has already failed and cannot be cancelled.";
                default -> "Only completed transfers can be cancelled. Current status: " + this.status;
            };
            throw new TransferNotCancellableException(
                "error.transfer_not_cancellable",
                new Object[]{this.status},
                detail
            );
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (this.createdAt.plusHours(cancellationWindowHours).isBefore(now)) {
            throw new TransferNotCancellableException(
                "error.transfer_cancellation_window_expired",
                new Object[]{this.createdAt, cancellationWindowHours},
                "Transfer was created " + cancellationWindowHours + " hours ago, cancellation window has passed. Created at: " + this.createdAt
            );
        }
        this.status = TransferStatus.CANCELLED;
        registerEvent(new TransferCancelledEvent(
                this.id, this.senderAccountId, this.receiverAccountId, this.amount, this.status, now));
    }
}
