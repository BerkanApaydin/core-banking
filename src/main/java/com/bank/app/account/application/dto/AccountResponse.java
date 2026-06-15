package com.bank.app.account.application.dto;

import com.bank.app.account.domain.Account;
import java.math.BigDecimal;

public record AccountResponse(
    Long id,
    String iban,
    String ownerName,
    BigDecimal balance,
    String currency,
    boolean active
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getIban().value(),
            account.getOwnerName(),
            account.getBalance().amount(),
            account.getBalance().currency().name(),
            account.isActive()
        );
    }
}
