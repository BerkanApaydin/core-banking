package com.bank.app.account.application.dto;

import com.bank.app.common.domain.Currency;
import java.math.BigDecimal;
import java.util.Objects;

public record CreateAccountRequest(
        Long userId,
        // Optional only for trusted demo seeding; web requests never supply an IBAN.
        String iban,
        String ownerName,
        BigDecimal initialBalance,
        Currency currency) {
    public CreateAccountRequest {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(initialBalance);
        Objects.requireNonNull(currency);
    }

    public CreateAccountRequest(Long userId, String ownerName, BigDecimal initialBalance, Currency currency) {
        this(userId, null, ownerName, initialBalance, currency);
    }
}
