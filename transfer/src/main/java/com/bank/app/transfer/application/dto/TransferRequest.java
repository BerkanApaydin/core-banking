package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Currency;
import java.math.BigDecimal;
import java.util.Objects;

public record TransferRequest(
        String senderIban,
        String receiverIban,
        BigDecimal amount,
        Currency currency) {
    public TransferRequest {
        Objects.requireNonNull(senderIban);
        Objects.requireNonNull(receiverIban);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
    }
}
