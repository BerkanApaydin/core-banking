package com.bank.app.common.domain;

import com.bank.app.common.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyEdgeCaseTest {

    @Test
    void shouldCreateMoneyWithZeroAmount() {
        Money money = Money.of("0.00", Currency.TRY);
        assertEquals(BigDecimal.ZERO.setScale(2), money.amount());
    }

    @Test
    void shouldCreateMoneyWithMaximumScale() {
        Money money = new Money(new BigDecimal("100.99"), Currency.TRY);
        assertEquals(2, money.amount().scale());
    }

    @Test
    void shouldThrowExceptionWhenScaleExceedsMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("100.999"), Currency.TRY));
    }

    @Test
    void shouldRoundAmountOnFactoryMethodWithExcessiveScale() {
        Money money = Money.of("100.999", Currency.TRY);
        assertEquals(new BigDecimal("101.00"), money.amount());
    }

    @Test
    void shouldRoundAmountFromStringOnFactoryMethod() {
        Money money = Money.of("100.456", Currency.TRY);
        assertEquals(new BigDecimal("100.46"), money.amount());
    }

    @Test
    void shouldRoundAmountOnBigDecimalFactory() {
        Money money = Money.of(new BigDecimal("100.456"), Currency.TRY);
        assertEquals(new BigDecimal("100.46"), money.amount());
    }

    @Test
    void shouldSubtractToZero() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money result = m1.subtract(Money.of("100.00", Currency.TRY));
        assertEquals(BigDecimal.ZERO.setScale(2), result.amount());
    }

    @Test
    void shouldNotReturnGreaterThanForEqualValues() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("100.00", Currency.TRY);
        assertFalse(m1.isGreaterThan(m2));
    }

    @Test
    void shouldReturnGreaterThanOrEqualForEqualValues() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("100.00", Currency.TRY);
        assertTrue(m1.isGreaterThanOrEqual(m2));
    }

    @Test
    void shouldThrowWhenSubtractionResultsInNegativeAmount() {
        Money m1 = Money.of("50.00", Currency.TRY);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> m1.subtract(Money.of("100.00", Currency.TRY)));
        assertEquals("Para tutarı negatif olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowCurrencyMismatchOnSubtractionWhenDifferentCurrencies() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.subtract(m2));
    }

    @Test
    void shouldThrowCurrencyMismatchOnBothComparisonMethods() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.isGreaterThan(m2));
        assertThrows(CurrencyMismatchException.class, () -> m1.isGreaterThanOrEqual(m2));
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("-0.01"), Currency.TRY));
        assertEquals("Para tutarı negatif olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowWhenNegativeStringOnFactory() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of("-100.00", Currency.TRY));
    }

    @Test
    void shouldThrowNullPointerOnNullFactoryString() {
        assertThrows(NullPointerException.class,
                () -> Money.of((String) null, Currency.TRY));
    }

    @Test
    void shouldThrowNullPointerOnNullFactoryBigDecimal() {
        assertThrows(NullPointerException.class,
                () -> Money.of((BigDecimal) null, Currency.TRY));
    }

    @Test
    void shouldAddMultipleTimesWithoutMutationOfOriginal() {
        Money original = Money.of("100.00", Currency.TRY);
        Money result1 = original.add(Money.of("50.00", Currency.TRY));
        Money result2 = original.add(Money.of("25.00", Currency.TRY));
        assertEquals(new BigDecimal("150.00"), result1.amount());
        assertEquals(new BigDecimal("125.00"), result2.amount());
        assertEquals(new BigDecimal("100.00"), original.amount());
    }

    @Test
    void shouldChainOperationsCorrectly() {
        Money m = Money.of("100.00", Currency.TRY);
        Money result = m.add(Money.of("50.00", Currency.TRY))
                .subtract(Money.of("30.00", Currency.TRY));
        assertEquals(new BigDecimal("120.00"), result.amount());
    }

    @Test
    void shouldHandleAllThreeCurrencies() {
        assertDoesNotThrow(() -> Money.of("100.00", Currency.TRY));
        assertDoesNotThrow(() -> Money.of("100.00", Currency.USD));
        assertDoesNotThrow(() -> Money.of("100.00", Currency.EUR));
    }

    @Test
    void shouldConvertToBigDecimalOfScale2() {
        Money money = Money.of("100.00", Currency.TRY);
        assertEquals(2, money.amount().scale());
    }

    @Test
    void shouldThrowSpecificMessageWhenAddWithNull() {
        Money money = Money.of("100.00", Currency.TRY);
        NullPointerException ex = assertThrows(NullPointerException.class, () -> money.add(null));
        assertEquals("Toplanacak para nesnesi null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowSpecificMessageWhenSubtractWithNull() {
        Money money = Money.of("100.00", Currency.TRY);
        NullPointerException ex = assertThrows(NullPointerException.class, () -> money.subtract(null));
        assertEquals("Çıkarılacak para nesnesi null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowSpecificMessageWhenIsGreaterThanWithNull() {
        Money money = Money.of("100.00", Currency.TRY);
        NullPointerException ex = assertThrows(NullPointerException.class, () -> money.isGreaterThan(null));
        assertEquals("Karşılaştırılacak para nesnesi null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowSpecificMessageWhenIsGreaterThanOrEqualWithNull() {
        Money money = Money.of("100.00", Currency.TRY);
        NullPointerException ex = assertThrows(NullPointerException.class, () -> money.isGreaterThanOrEqual(null));
        assertEquals("Karşılaştırılacak para nesnesi null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowCurrencyMismatchOnAddWhenDifferentCurrencies() {
        Money m1 = Money.of("100.00", Currency.TRY);
        Money m2 = Money.of("50.00", Currency.USD);
        assertThrows(CurrencyMismatchException.class, () -> m1.add(m2));
    }

    @Test
    void shouldCreateMoneyWithZeroBigDecimalDirectly() {
        Money money = new Money(BigDecimal.ZERO.setScale(2), Currency.TRY);
        assertEquals(BigDecimal.ZERO.setScale(2), money.amount());
    }

    @Test
    void isGreaterThanShouldReturnTrueWhenGreater() {
        Money larger = Money.of("200.00", Currency.TRY);
        Money smaller = Money.of("100.00", Currency.TRY);
        assertTrue(larger.isGreaterThan(smaller));
    }

    @Test
    void isGreaterThanOrEqualShouldReturnTrueWhenGreater() {
        Money larger = Money.of("200.00", Currency.TRY);
        Money smaller = Money.of("100.00", Currency.TRY);
        assertTrue(larger.isGreaterThanOrEqual(smaller));
    }
}
