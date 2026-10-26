package com.bank.app.transfer.domain;

import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.BalanceLimits;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.InvalidAmountException;
import com.bank.app.common.messages.MessageKeys;
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

    private Transfer(Long id, AccountId senderAccountId, AccountId receiverAccountId, Money amount, TransferStatus status, LocalDateTime createdAt, Long version) {
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
        Objects.requireNonNull(senderAccountId, "Sender account ID must not be null");
        Objects.requireNonNull(receiverAccountId, "Receiver account ID must not be null");
        Objects.requireNonNull(amount, "Transfer amount must not be null");
        Objects.requireNonNull(clock, "Clock must not be null");
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
        return new Transfer(null, senderAccountId, receiverAccountId, amount, TransferStatus.PENDING, LocalDateTime.now(clock), null);
    }

    /**
     * The only entry point for materializing persisted state. Same-account and
     * zero-amount invariants are re-validated so a corrupt row can never
     * materialize an illegal aggregate.
     */
    public static Transfer rehydrate(Long id, AccountId senderAccountId, AccountId receiverAccountId,
            Money amount, TransferStatus status, LocalDateTime createdAt) {
        return rehydrate(id, senderAccountId, receiverAccountId, amount, status, createdAt, null);
    }

    public static Transfer rehydrate(Long id, AccountId senderAccountId, AccountId receiverAccountId,
            Money amount, TransferStatus status, LocalDateTime createdAt, Long version) {
        return new Transfer(id, senderAccountId, receiverAccountId, amount, status, createdAt, version);
    }

    public void complete(Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status);
        }
        // Build the event BEFORE mutating the aggregate: the event requires a
        // non-null id, and a throw after the status flip would leave a
        // COMPLETED transfer with no TransferCompletedEvent — money moved but
        // no notification or audit row.
        TransferCompletedEvent event = new TransferCompletedEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount,
                TransferStatus.COMPLETED, LocalDateTime.now(clock));
        this.status = TransferStatus.COMPLETED;
        registerEvent(event);
    }

    private static void requireNonZero(Money amount) {
        if (amount.isZero()) {
            throw new InvalidAmountException("Transfer amount must not be zero");
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
     * transfer behind. There is no production caller today: the method and
     * {@code TransferStatus.FAILED} are retained for the documented async
     * placement path (see docs/decisions/transfer-failed-state.md), where a
     * worker must reconcile a PENDING row that outlived its placement.
     */
    public void markFailed(Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        if (this.status != TransferStatus.PENDING) {
            throw new TransferNotPendingException(this.status, "marked as failed");
        }
        // Same ordering rule as complete(): never flip the status before the
        // event can be built.
        TransferFailedEvent event = new TransferFailedEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount,
                TransferStatus.FAILED, LocalDateTime.now(clock));
        this.status = TransferStatus.FAILED;
        registerEvent(event);
    }

    public void cancel(Clock clock, Duration cancellationWindow) {
        Objects.requireNonNull(clock, "Clock must not be null");
        Objects.requireNonNull(cancellationWindow, "Cancellation window must not be null");
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
                MessageKeys.TRANSFER_NOT_CANCELLABLE,
                new Object[]{this.status},
                detail
            );
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (this.createdAt.plus(cancellationWindow).isBefore(now)) {
            throw new TransferNotCancellableException(
                MessageKeys.TRANSFER_CANCELLATION_WINDOW_EXPIRED,
                new Object[]{this.createdAt, cancellationWindow.toHours()},
                "Transfer was created " + cancellationWindow.toHours() + " hours ago, cancellation window has passed. Created at: " + this.createdAt
            );
        }
        // Same ordering rule as complete(): build first, then mutate, so a
        // failure to build the event cannot leave a CANCELLED aggregate with
        // no TransferCancelledEvent (the refund leg may already have run).
        TransferCancelledEvent event = new TransferCancelledEvent(
                this.id, this.senderAccountId.value(), this.receiverAccountId.value(), this.amount,
                TransferStatus.CANCELLED, now);
        this.status = TransferStatus.CANCELLED;
        registerEvent(event);
    }
}
