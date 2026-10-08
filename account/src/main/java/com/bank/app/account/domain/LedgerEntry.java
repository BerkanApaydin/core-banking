package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * One leg of a double-entry operation. Immutable: journal rows are
 * append-only, never updated. Every business operation writes its legs with
 * one shared {@code transactionRef} inside the same local transaction as the
 * balance mutation, so the legs of a ref always net to zero (see V29).
 */
public final class LedgerEntry {

    private final Long id;
    private final String transactionRef;
    private final Long accountId;
    private final LedgerDirection direction;
    private final Money amount;
    private final Money balanceAfter;
    private final LocalDateTime occurredAt;

    public LedgerEntry(Long id, String transactionRef, Long accountId, LedgerDirection direction,
                       Money amount, Money balanceAfter, LocalDateTime occurredAt) {
        this.id = id;
        Objects.requireNonNull(transactionRef, "Transaction ref must not be null");
        if (transactionRef.isBlank()) {
            throw new IllegalArgumentException("Transaction ref must not be blank");
        }
        this.transactionRef = transactionRef;
        this.accountId = Objects.requireNonNull(accountId, "Account ID must not be null");
        this.direction = Objects.requireNonNull(direction, "Direction must not be null");
        this.amount = Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.isZero()) {
            throw new IllegalArgumentException("Ledger amount must not be zero");
        }
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "Balance after must not be null");
        if (amount.currency() != balanceAfter.currency()) {
            throw new CurrencyMismatchException(
                    amount.currency() + " and " + balanceAfter.currency() + " cannot be journaled together");
        }
        this.occurredAt = Objects.requireNonNull(occurredAt, "Occurred-at must not be null");
    }

    /** Groups the legs of one business operation (debit + credit share it). */
    public static String newTransactionRef() {
        return UUID.randomUUID().toString();
    }

    public static LedgerEntry debit(Long accountId, Money amount, Money balanceAfter,
                                    String transactionRef, Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        return new LedgerEntry(null, transactionRef, accountId, LedgerDirection.DEBIT,
                amount, balanceAfter, LocalDateTime.now(clock));
    }

    public static LedgerEntry credit(Long accountId, Money amount, Money balanceAfter,
                                     String transactionRef, Clock clock) {
        Objects.requireNonNull(clock, "Clock must not be null");
        return new LedgerEntry(null, transactionRef, accountId, LedgerDirection.CREDIT,
                amount, balanceAfter, LocalDateTime.now(clock));
    }

    /**
     * Preferred factories: derive {@code balanceAfter} from
     * {@code balanceBefore} inside the journal, so callers cannot persist an
     * inconsistent {@code balanceAfter}. Existing {@code balanceAfter}
     * overloads are kept for backward compatibility (mappers, replays).
     */
    public static LedgerEntry debitFromBefore(Long accountId, Money amount, Money balanceBefore,
                                              String transactionRef, Clock clock) {
        Objects.requireNonNull(balanceBefore, "Balance before must not be null");
        return debit(accountId, amount, balanceBefore.subtract(amount), transactionRef, clock);
    }

    public static LedgerEntry creditFromBefore(Long accountId, Money amount, Money balanceBefore,
                                               String transactionRef, Clock clock) {
        Objects.requireNonNull(balanceBefore, "Balance before must not be null");
        return credit(accountId, amount, balanceBefore.add(amount), transactionRef, clock);
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public Long getAccountId() {
        return accountId;
    }

    public LedgerDirection getDirection() {
        return direction;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "LedgerEntry{id=" + id + ", ref=" + transactionRef + ", account=" + accountId
                + ", direction=" + direction + ", amount=" + amount + "}";
    }
}
