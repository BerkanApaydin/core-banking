package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.TransferAlreadyCancelledException;
import com.bank.app.common.exception.TransferNotCancellableException;
import org.springframework.lang.NonNull;
import java.time.LocalDateTime;
import java.util.Objects;

public class Transfer {
    private static final int CANCELLATION_WINDOW_HOURS = 24;

    private final Long id;
    @NonNull private final Long senderAccountId;
    @NonNull private final Long receiverAccountId;
    @NonNull private final Money amount;
    @NonNull private TransferStatus status;
    @NonNull private final LocalDateTime createdAt;
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
        return new Transfer(null, senderAccountId, receiverAccountId, amount, TransferStatus.COMPLETED, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    @NonNull
    public Long getSenderAccountId() {
        return senderAccountId;
    }

    @NonNull
    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    @NonNull
    public Money getAmount() {
        return amount;
    }

    @NonNull
    public TransferStatus getStatus() {
        return status;
    }

    @NonNull
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void cancel() {
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
        if (this.createdAt.plusHours(CANCELLATION_WINDOW_HOURS).isBefore(LocalDateTime.now())) {
            throw new TransferNotCancellableException(
                "error.transfer_cancellation_window_expired",
                new Object[]{this.createdAt, CANCELLATION_WINDOW_HOURS},
                "Transfer üzerinden " + CANCELLATION_WINDOW_HOURS + " saat geçtiği için iptal edilemez. Oluşturulma zamanı: " + this.createdAt
            );
        }
        this.status = TransferStatus.CANCELLED;
    }
}
