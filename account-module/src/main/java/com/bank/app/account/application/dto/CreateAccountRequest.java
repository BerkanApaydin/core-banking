package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Currency;
import java.math.BigDecimal;
import java.util.Objects;

public record CreateAccountRequest(
        Long userId,
        String iban,
        String ownerName,
        BigDecimal initialBalance,
        Currency currency) {
    public CreateAccountRequest {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(iban);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(initialBalance);
        Objects.requireNonNull(currency);
    }
}
