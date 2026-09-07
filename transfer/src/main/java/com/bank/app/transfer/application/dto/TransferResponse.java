package com.bank.app.transfer.application.dto;

import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record TransferResponse(
        Long id,
        TransferStatus status,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt,
        String senderIban,
        String receiverIban,
        Long senderAccountId,
        Long receiverAccountId) {
    public TransferResponse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(senderIban);
        Objects.requireNonNull(receiverIban);
        Objects.requireNonNull(senderAccountId);
        Objects.requireNonNull(receiverAccountId);
    }

    public static TransferResponse from(Transfer transfer, String senderIban, String receiverIban) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getStatus(),
                transfer.getAmount().amount(),
                transfer.getAmount().currency().name(),
                transfer.getCreatedAt(),
                senderIban,
                receiverIban,
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId());
    }
}
