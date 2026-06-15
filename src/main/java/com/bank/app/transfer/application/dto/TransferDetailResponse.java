package com.bank.app.transfer.application.dto;

import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferDetailResponse(
    Long id,
    Long senderAccountId,
    Long receiverAccountId,
    BigDecimal amount,
    String currency,
    String status,
    LocalDateTime createdAt
) {
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
