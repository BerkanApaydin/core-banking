package com.bank.app.transfer.application.dto;

import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransferDetailResponse(
    Long id,
    Long senderAccountId,
    Long receiverAccountId,
    BigDecimal amount,
    String currency,
    TransferStatus status,
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
        // Wire DTO stays on Long; unwrap at this edge.
        return new TransferDetailResponse(
            transfer.getId(),
            transfer.getSenderAccountId().value(),
            transfer.getReceiverAccountId().value(),
            transfer.getAmount().amount(),
            transfer.getAmount().currency().name(),
            transfer.getStatus(),
            transfer.getCreatedAt()
        );
    }
}
