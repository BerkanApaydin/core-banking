package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.BalanceLimits;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import com.bank.app.transfer.domain.exception.TransferNotPendingException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transfer extends BaseAggregateRoot {

    private final Long id;
    private final AccountId senderAccountId;
    private final AccountId receiverAccountId;
    private final Money amount;
    private TransferStatus status;
    private final LocalDateTime createdAt;
    private final Long version;

    public Transfer(Long id, AccountId senderAccountId, AccountId receiverAccountId, Money amount, TransferStatus status, LocalDateTime createdAt) {
        this(id, senderAccountId, receiverAccountId, amount, status, createdAt, null);
    }

    public Transfer(Long id, AccountId senderAccountId, AccountId receiverAccountId, Money amount, TransferStatus status, LocalDateTime createdAt, Long version) {
        this.id = id;
        this.senderAccountId = Objects.requireNonNull(senderAccountId, "Sender account ID must not be null");
        this.receiverAccountId = Objects.requireNonNull(receiverAccountId, "Receiver account ID must not be null");
        if (Objects.equals(senderAccountId, receiverAccountId)) {
            throw new SameAccountTransferException(String.valueOf(senderAccountId));
        }
        this.amount = Objects.requireNonNull(amount, "Transfer amount must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
        // Zero is rejected for every status, not just PENDING: the factory
        // already enforces this, and the constructor must be equally strict so
        // rehydrated/legacy rows can never materialize a zero transfer.
        requireNonZero(amount);
        this.createdAt = Objects.requireNonNull(createdAt, "Created date must not be null");
        this.version = version;
    }

    public static Transfer create(AccountId senderAccountId, AccountId receiverAccountId, Money amount, Clock clock) {
        Objects.requireNonNull(amount, "Transfer amount must not be null");
        // D-1 inner guarantee: even a direct Transfer.create caller cannot
        // exceed the ceiling (TransferDomainService is the normal path).
        // Zero rejection lives in the constructor (single enforcement point):
        // a duplicate requireNonZero here would be an equivalent mutant.
        if (amount.amount().compareTo(BalanceLimits.MAX_BALANCE_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Transfer amount exceeds maximum of " + BalanceLimits.MAX_BALANCE);
        }
        if (Objects.equals(senderAccountId, receiverAccountId)) {
            throw new SameAccountTransferException(String.valueOf(senderAccountId));
        }
        return new Transfer(null, senderAccountId, receiverAccountId, amount, TransferStatus.PENDING, LocalDateTime.now(clock));
    }

    public void complete(Clock clock) {
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status);
        }
        this.status = TransferStatus.COMPLETED;
        registerEvent(new TransferCompletedEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount, this.status, LocalDateTime.now(clock)));
    }

    private static void requireNonZero(Money amount) {
        if (amount.isZero()) {
            throw new IllegalArgumentException("Transfer amount must not be zero");
        }
    }

    public Long getId() {
        return id;
    }

    public AccountId getSenderAccountId() {
        return senderAccountId;
    }

    public AccountId getReceiverAccountId() {
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

    /**
     * Marks a pending transfer as failed. The synchronous placement path rolls
     * the transaction back instead of persisting a FAILED row (see
     * PlaceTransferUseCaseImpl), so a failure never leaves a half-written
     * transfer behind. The production caller for crash leftovers is
     * {@code TransferPendingReaper}, which transitions stale PENDING rows
     * (JVM crash between the two saves) to FAILED through this method —
     * together with {@code TransferStatus.FAILED} and the report UI that
     * already renders it.
     */
    public void markFailed(Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status);
        }
        this.status = TransferStatus.FAILED;
        registerEvent(new TransferFailedEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount, this.status, LocalDateTime.now(clock)));
    }

    public void cancel(Clock clock, Duration cancellationWindow) {
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
        Objects.requireNonNull(cancellationWindow, "Cancellation window must not be null");
        LocalDateTime now = LocalDateTime.now(clock);
        if (this.createdAt.plus(cancellationWindow).isBefore(now)) {
            throw new TransferNotCancellableException(
                "error.transfer_cancellation_window_expired",
                new Object[]{this.createdAt, cancellationWindow.toHours()},
                "Transfer was created " + cancellationWindow.toHours() + " hours ago, cancellation window has passed. Created at: " + this.createdAt
            );
        }
        this.status = TransferStatus.CANCELLED;
        registerEvent(new TransferCancelledEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount, this.status, now));
    }
}
