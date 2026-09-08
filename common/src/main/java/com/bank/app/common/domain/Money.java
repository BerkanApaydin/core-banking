package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

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
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        return new Money(amount.setScale(2, RoundingMode.HALF_UP), currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other, "cannot be added");
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other, "cannot be subtracted");
        return new Money(this.amount.subtract(other.amount), this.currency);
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
