package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transfer {

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
        this.senderAccountId = Objects.requireNonNull(senderAccountId, "Gönderici hesap ID null olamaz");
        this.receiverAccountId = Objects.requireNonNull(receiverAccountId, "Alıcı hesap ID null olamaz");
        this.amount = Objects.requireNonNull(amount, "Transfer tutarı null olamaz");
        this.status = Objects.requireNonNull(status, "Durum null olamaz");
        this.createdAt = Objects.requireNonNull(createdAt, "Oluşturulma tarihi null olamaz");
        this.version = version;
    }

    public static Transfer create(Long senderAccountId, Long receiverAccountId, Money amount) {
        return create(senderAccountId, receiverAccountId, amount, Clock.systemDefaultZone());
    }

    public static Transfer create(Long senderAccountId, Long receiverAccountId, Money amount, Clock clock) {
        return new Transfer(null, senderAccountId, receiverAccountId, amount, TransferStatus.PENDING, LocalDateTime.now(clock));
    }

    public void complete() {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Sadece PENDING durumundaki transferler tamamlanabilir. Mevcut durum: " + this.status);
        }
        this.status = TransferStatus.COMPLETED;
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
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "Transfer{id=" + id + ", sender=" + senderAccountId + ", receiver=" + receiverAccountId
                + ", amount=" + amount + ", status=" + status + "}";
    }

    public void cancel(int cancellationWindowHours) {
        cancel(cancellationWindowHours, Clock.systemDefaultZone());
    }

    public void cancel(int cancellationWindowHours, Clock clock) {
        if (this.status == TransferStatus.CANCELLED) {
            throw new TransferAlreadyCancelledException(this.id);
        }
        if (this.status != TransferStatus.COMPLETED) {
            throw new TransferNotCancellableException(
                "error.transfer_not_cancellable",
                new Object[]{this.status},
                "Sadece tamamlanmış transferler iptal edilebilir. Mevcut durum: " + this.status
            );
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (this.createdAt.plusHours(cancellationWindowHours).isBefore(now)) {
            throw new TransferNotCancellableException(
                "error.transfer_cancellation_window_expired",
                new Object[]{this.createdAt, cancellationWindowHours},
                "Transfer üzerinden " + cancellationWindowHours + " saat geçtiği için iptal edilemez. Oluşturulma zamanı: " + this.createdAt
            );
        }
        this.status = TransferStatus.CANCELLED;
    }
}
