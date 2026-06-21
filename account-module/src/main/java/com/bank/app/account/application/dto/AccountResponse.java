package com.bank.app.account.application.dto;

import java.math.BigDecimal;

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
        boolean active) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUserId(),
                account.getIban().value(),
                account.getOwnerName(),
                account.getBalance().amount(),
                account.getBalance().currency().name(),
                account.getStatus(),
                account.getStatus() == AccountStatus.ACTIVE);
    }
}
