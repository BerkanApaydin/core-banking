package com.bank.app.account.application.dto;

import java.math.BigDecimal;
import java.util.Objects;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;

public record AccountResponse(
        Long id,
        Long userId,
        String iban,
        String ownerName,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        boolean active,
        // I-10: optimistic-concurrency version for If-Match/ETag clients.
        // Nullable: pre-persistence aggregates carry no version yet.
        Long version) {
    public AccountResponse {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(iban);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(balance);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(status);
    }

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUserId().value(),
                account.getIban().value(),
                account.getOwnerName(),
                account.getBalance().amount(),
                account.getBalance().currency().name(),
                account.getStatus(),
                account.getStatus() == AccountStatus.ACTIVE,
                account.getVersion());
    }
}
