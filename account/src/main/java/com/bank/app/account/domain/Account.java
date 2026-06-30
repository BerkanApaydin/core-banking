package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.AccountClosedException;
import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.domain.BaseAggregateRoot;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

public class Account extends BaseAggregateRoot {
    private final Long id;
    private final UserId userId;
    private final Iban iban;
    private final String ownerName;
    private Money balance;
    private AccountStatus status;
    private final Long version;
    private static final Clock DEFAULT_CLOCK = Clock.systemDefaultZone();

    public Account(Long id, UserId userId, Iban iban, String ownerName, Money balance, AccountStatus status) {
        this(id, userId, iban, ownerName, balance, status, null);
    }

    public Account(Long id, UserId userId, Iban iban, String ownerName, Money balance, AccountStatus status, Long version) {
        this.id = id;
        this.userId = Objects.requireNonNull(userId, "UserId must not be null");
        this.iban = Objects.requireNonNull(iban, "IBAN must not be null");
        this.ownerName = Objects.requireNonNull(ownerName, "Owner name must not be null");
        if (ownerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Owner name must not be empty");
        }
        if (ownerName.trim().length() > 255) {
            throw new IllegalArgumentException("Owner name can be at most 255 characters");
        }
        this.balance = Objects.requireNonNull(balance, "Balance must not be null");
        this.status = Objects.requireNonNull(status, "Account status must not be null");
        this.version = version;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private UserId userId;
        private Iban iban;
        private String ownerName;
        private Money balance;
        private AccountStatus status;
        private Long version;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder userId(UserId userId) { this.userId = userId; return this; }
        public Builder iban(Iban iban) { this.iban = iban; return this; }
        public Builder ownerName(String ownerName) { this.ownerName = ownerName; return this; }
        public Builder balance(Money balance) { this.balance = balance; return this; }
        public Builder status(AccountStatus status) { this.status = status; return this; }
        public Builder version(Long version) { this.version = version; return this; }

        public Account build() {
            return new Account(id, userId, iban, ownerName, balance, status, version);
        }
    }

    public UserId getUserId() {
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
        debit(amount, DEFAULT_CLOCK);
    }

    public void debit(Money amount, Clock clock) {
        Objects.requireNonNull(amount, "Debit amount must not be null");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Debit amount must not be zero");
        }
        if (!isActive()) {
            throw new AccountNotActiveException(this.iban.value());
        }
        if (!this.balance.isGreaterThanOrEqual(amount)) {
            throw new InsufficientBalanceException(
                "error.insufficient_balance",
                new Object[]{this.balance.amount(), this.balance.currency().name(), amount.amount(), amount.currency().name()},
                "Insufficient balance. Current: " + this.balance.amount() + " " + this.balance.currency() +
                ", Requested: " + amount.amount() + " " + amount.currency()
            );
        }
        this.balance = this.balance.subtract(amount);
        registerEvent(new AccountDebitedEvent(this.id, amount, this.balance, LocalDateTime.now(clock)));
    }

    public void credit(Money amount) {
        credit(amount, DEFAULT_CLOCK);
    }

    public void credit(Money amount, Clock clock) {
        Objects.requireNonNull(amount, "Credit amount must not be null");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Credit amount must not be zero");
        }
        if (!isActive()) {
            throw new AccountNotActiveException(this.iban.value());
        }
        this.balance = this.balance.add(amount);
        registerEvent(new AccountCreditedEvent(this.id, amount, this.balance, LocalDateTime.now(clock)));
    }

    public void suspend() {
        suspend(DEFAULT_CLOCK);
    }

    public void suspend(Clock clock) {
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountClosedException(this.iban.value());
        }
        if (this.status == AccountStatus.SUSPENDED) {
            return;
        }
        this.status = AccountStatus.SUSPENDED;
        registerEvent(new AccountSuspendedEvent(this.id, LocalDateTime.now(clock)));
    }

    public void close() {
        close(DEFAULT_CLOCK);
    }

    public void close(Clock clock) {
        if (this.status == AccountStatus.CLOSED) {
            throw new AccountClosedException(this.iban.value());
        }
        if (!this.balance.isZero()) {
            throw new InsufficientBalanceException(
                "error.account_close_balance_not_zero",
                new Object[]{this.balance.amount(), this.balance.currency().name()},
                "Balance must be zero for closure. Current: " + this.balance.amount() + " " + this.balance.currency()
            );
        }
        this.status = AccountStatus.CLOSED;
        registerEvent(new AccountClosedEvent(this.id, this.balance, LocalDateTime.now(clock)));
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
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{id=" + id + ", iban=" + iban.value() + ", status=" + status + ", balance=" + balance + "}";
    }
}

