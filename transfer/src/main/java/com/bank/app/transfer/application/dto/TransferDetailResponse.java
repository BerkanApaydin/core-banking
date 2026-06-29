package com.bank.app.transfer.application.dto;

import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransferDetailResponse(
    Long id,
    Long senderAccountId,
    Long receiverAccountId,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime createdAt
) {
    public TransferDetailResponse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(senderAccountId);
        Objects.requireNonNull(receiverAccountId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    public static TransferDetailResponse from(Transfer transfer) {
        return new TransferDetailResponse(
            transfer.getId(),
            transfer.getSenderAccountId(),
            transfer.getReceiverAccountId(),
            transfer.getAmount().amount(),
            transfer.getAmount().currency().name(),
            transfer.getStatus().name(),
            transfer.getCreatedAt()
        );
    }
}
