package com.bank.app.common.domain;

import com.bank.app.common.exception.CurrencyMismatchException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(
        BigDecimal amount,
        Currency currency) {
    private static final int MAX_SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "Tutar boş olamaz");
        Objects.requireNonNull(currency, "Para birimi boş olamaz");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Para tutarı negatif olamaz");
        }
        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException("Para en fazla " + MAX_SCALE + " ondalık basamak olabilir");
        }
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP), currency);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount.setScale(2, RoundingMode.HALF_UP), currency);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "Toplanacak para nesnesi null olamaz");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    this.currency + " ile " + other.currency + " toplanamaz");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "Çıkarılacak para nesnesi null olamaz");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    this.currency + " ile " + other.currency + " çıkarılamaz");
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        Objects.requireNonNull(other, "Karşılaştırılacak para nesnesi null olamaz");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    this.currency + " ile " + other.currency + " karşılaştırılamaz");
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        Objects.requireNonNull(other, "Karşılaştırılacak para nesnesi null olamaz");
        if (this.currency != other.currency) {
            throw new CurrencyMismatchException(
                    this.currency + " ile " + other.currency + " karşılaştırılamaz");
        }
        return this.amount.compareTo(other.amount) >= 0;
    }
}
