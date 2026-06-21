package com.bank.app.common.domain;

import com.bank.app.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneyWhenValuesAreValid() {
        Money money = Money.of("100.50", Currency.TRY);
        assertEquals(new BigDecimal("100.50"), money.amount());
        assertEquals(Currency.TRY, money.currency());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenScaleIsTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("100.555"), Currency.TRY));
    }

    @Test
    void shouldAddMoneyWithSameCurrency() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.25", Currency.TRY);
        Money result = m1.add(m2);
        assertEquals(new BigDecimal("150.25"), result.amount());
    }

    @Test
    void shouldSubtractMoneyWithSameCurrency() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("30.00", Currency.TRY);
        Money result = m1.subtract(m2);
        assertEquals(new BigDecimal("70.00"), result.amount());
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionOnAdditionOfDifferentCurrencies() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.add(m2));
    }

    @Test
    void shouldCompareGreaterAndEqualCorrectly() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.TRY);
        Money m3 = Money.of("100.00", Currency.TRY);

        assertTrue(m1.isGreaterThan(m2));
        assertFalse(m2.isGreaterThan(m1));
        assertTrue(m1.isGreaterThanOrEqual(m3));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenConstructorArgsAreNull() {
        assertThrows(NullPointerException.class, () -> new Money(null, Currency.TRY));
        assertThrows(NullPointerException.class, () -> new Money(BigDecimal.ONE, null));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenOtherArgIsNull() {
        Money money = Money.of("100.00", Currency.TRY);
        assertThrows(NullPointerException.class, () -> money.add(null));
        assertThrows(NullPointerException.class, () -> money.subtract(null));
        assertThrows(NullPointerException.class, () -> money.isGreaterThan(null));
        assertThrows(NullPointerException.class, () -> money.isGreaterThanOrEqual(null));
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionOnComparisonOfDifferentCurrencies() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.isGreaterThan(m2));
        assertThrows(CurrencyMismatchException.class, () -> m1.isGreaterThanOrEqual(m2));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Money(new BigDecimal("-10.00"), Currency.TRY));
    }

    @Test
    void shouldCreateMoneyFromBigDecimal() {
        Money money = Money.of(new BigDecimal("100.00"), Currency.TRY);
        assertEquals(new BigDecimal("100.00"), money.amount());
        assertEquals(Currency.TRY, money.currency());
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionOnSubtractionOfDifferentCurrencies() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.subtract(m2));
    }
}
