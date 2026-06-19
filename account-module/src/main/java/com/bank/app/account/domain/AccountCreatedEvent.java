package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import java.util.Objects;

public class AccountCreatedEvent {
    private final Long accountId;
    private final Long userId;
    private final String iban;
    private final String ownerName;
    private final Money balance;

    public AccountCreatedEvent(Long accountId, Long userId, String iban, String ownerName, Money balance) {
        this.accountId = Objects.requireNonNull(accountId, "accountId null olamaz");
        this.userId = Objects.requireNonNull(userId, "userId null olamaz");
        this.iban = Objects.requireNonNull(iban, "iban null olamaz");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName null olamaz");
        this.balance = Objects.requireNonNull(balance, "balance null olamaz");
    }

    public Long getAccountId() { return accountId; }
    public Long getUserId() { return userId; }
    public String getIban() { return iban; }
    public String getOwnerName() { return ownerName; }
    public Money getBalance() { return balance; }
}
