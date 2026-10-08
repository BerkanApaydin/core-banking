package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

public record Money(
        BigDecimal amount,
        Currency currency) {
    private static final int MAX_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("Amount can have at most " + MAX_SCALE + " decimal places");
        }
    }

    public static Money of(String amount, Currency currency) {
        return exact(amount, currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return exact(amount, currency);
    }

    /**
     * D-1: single-transfer ceiling enforced in the domain (not only in web
     * DTOs). {@link BalanceLimits#MAX_BALANCE_AMOUNT} is the single source of
     * truth; web {@code @DecimalMax} mirrors the same literal.
     */
    public static Money ofTransferAmount(BigDecimal amount, Currency currency) {
        Money m = exact(amount, currency);
        if (m.amount().compareTo(BalanceLimits.MAX_BALANCE_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Transfer amount exceeds maximum of " + BalanceLimits.MAX_BALANCE);
        }
        return m;
    }

    public static Money ofTransferAmount(String amount, Currency currency) {
        return ofTransferAmount(new BigDecimal(amount), currency);
    }

    /** Creates a monetary amount without changing its numeric value. */
    public static Money exact(String amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        return exact(new BigDecimal(amount), currency);
    }

    public static Money exact(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        requireNonNegative(amount);
        // Validate BEFORE normalizing: setScale(UNNECESSARY) throws
        // ArithmeticException (mapped to 500) on excess scale. The domain
        // contract is IllegalArgumentException (mapped to 400 INVALID_ARGUMENT),
        // consistent with the canonical constructor below.
        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException(
                    "Amount can have at most " + MAX_SCALE + " decimal places: " + amount);
        }
        return new Money(amount.setScale(MAX_SCALE, RoundingMode.UNNECESSARY), currency);
    }

    /** Applies an explicit HALF_UP policy, suitable only when rounding is intended. */
    public static Money rounded(String amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        return rounded(new BigDecimal(amount), currency);
    }

    public static Money rounded(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        requireNonNegative(amount);
        return new Money(amount.setScale(MAX_SCALE, RoundingMode.HALF_UP), currency);
    }

    private static void requireNonNegative(BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other, "cannot be added");
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other, "cannot be subtracted");
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Non-throwing subtraction: empty when currencies differ or the result
     * would be negative. Callers that need a domain failure (e.g.
     * {@code InsufficientBalanceException}) should pre-check with
     * {@link #isGreaterThanOrEqual(Money)} and keep {@link #subtract(Money)}.
     */
    public Optional<Money> trySubtract(Money other) {
        if (other == null || this.currency != other.currency) {
            return Optional.empty();
        }
        if (this.amount.compareTo(other.amount) < 0) {
            return Optional.empty();
        }
        return Optional.of(new Money(this.amount.subtract(other.amount), this.currency));
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other, "cannot be compared");
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        requireSameCurrency(other, "cannot be compared");
        return this.amount.compareTo(other.amount) >= 0;
    }

    private void requireSameCurrency(Money other, String operation) {
        Objects.requireNonNull(other, "Money object must not be null");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    this.currency + " and " + other.currency + " " + operation);
        }
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        // BigDecimal.equals is scale-sensitive ("10.0" != "10.00"); money
        // equality must be numeric, so compare with compareTo == 0.
        return this.currency == other.currency
                && this.amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        // Normalize scale so equal amounts hash equally.
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
