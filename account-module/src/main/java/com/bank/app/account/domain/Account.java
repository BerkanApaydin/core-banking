package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.domain.Money;
import java.util.Objects;

public class Account {
    private final Long id;
    private final Long userId;
    private final Iban iban;
    private final String ownerName;
    private Money balance;
    private final boolean active;
    private final Long version;

    public Account(Long id, Long userId, Iban iban, String ownerName, Money balance, boolean active) {
        this(id, userId, iban, ownerName, balance, active, null);
    }

    public Account(Long id, Long userId, Iban iban, String ownerName, Money balance, boolean active, Long version) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "UserId null olamaz");
        this.iban = Objects.requireNonNull(iban, "IBAN null olamaz");
        this.ownerName = Objects.requireNonNull(ownerName, "OwnerName null olamaz");
        if (ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sahip adı boş olamaz");
        }
        this.balance = Objects.requireNonNull(balance, "Bakiye null olamaz");
        this.active = active;
        this.version = version;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getId() {
        return id;
    }

    public Iban getIban() {
        return iban;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Money getBalance() {
        return balance;
    }

    public boolean isActive() {
        return active;
    }

    public void debit(Money amount) {
        Objects.requireNonNull(amount, "Düşülecek tutar null olamaz");
        if (!this.active) {
            throw new AccountNotActiveException(this.iban.value());
        }
        if (!this.balance.isGreaterThanOrEqual(amount)) {
            throw new InsufficientBalanceException(
                "error.insufficient_balance",
                new Object[]{this.balance.amount(), this.balance.currency().name(), amount.amount(), amount.currency().name()},
                "Bakiye yetersiz. Mevcut: " + this.balance.amount() + " " + this.balance.currency() +
                ", İstenen: " + amount.amount() + " " + amount.currency()
            );
        }
        this.balance = this.balance.subtract(amount);
    }

    public void credit(Money amount) {
        Objects.requireNonNull(amount, "Eklenecek tutar null olamaz");
        if (!this.active) {
            throw new AccountNotActiveException(this.iban.value());
        }
        this.balance = this.balance.add(amount);
    }

    public Long getVersion() {
        return version;
    }
}

