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
    private AccountStatus status;
    private final Long version;

    public Account(Long id, Long userId, Iban iban, String ownerName, Money balance, AccountStatus status) {
        this(id, userId, iban, ownerName, balance, status, null);
    }

    public Account(Long id, Long userId, Iban iban, String ownerName, Money balance, AccountStatus status, Long version) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "UserId null olamaz");
        this.iban = Objects.requireNonNull(iban, "IBAN null olamaz");
        this.ownerName = Objects.requireNonNull(ownerName, "OwnerName null olamaz");
        if (ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Sahip adı boş olamaz");
        }
        this.balance = Objects.requireNonNull(balance, "Bakiye null olamaz");
        this.status = Objects.requireNonNull(status, "Hesap durumu null olamaz");
        this.version = version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long userId;
        private Iban iban;
        private String ownerName;
        private Money balance;
        private AccountStatus status;
        private Long version;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder iban(Iban iban) { this.iban = iban; return this; }
        public Builder ownerName(String ownerName) { this.ownerName = ownerName; return this; }
        public Builder balance(Money balance) { this.balance = balance; return this; }
        public Builder status(AccountStatus status) { this.status = status; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Account build() {
            return new Account(id, userId, iban, ownerName, balance, status, version);
        }
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

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void debit(Money amount) {
        Objects.requireNonNull(amount, "Düşülecek tutar null olamaz");
        if (!isActive()) {
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
        if (!isActive()) {
            throw new AccountNotActiveException(this.iban.value());
        }
        this.balance = this.balance.add(amount);
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}

