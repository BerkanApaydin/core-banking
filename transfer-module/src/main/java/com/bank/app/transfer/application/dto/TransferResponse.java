package com.bank.app.transfer.application.dto;

import com.bank.app.transfer.domain.Transfer;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long id,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime createdAt,
        String senderIban,
        String receiverIban,
        Long senderAccountId,
        Long receiverAccountId) {
    public static TransferResponse from(Transfer transfer, String senderIban, String receiverIban) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getStatus().name(),
                transfer.getAmount().amount(),
                transfer.getAmount().currency().name(),
                transfer.getCreatedAt(),
                senderIban,
                receiverIban,
                transfer.getSenderAccountId(),
                transfer.getReceiverAccountId());
    }
}
