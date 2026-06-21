package com.bank.app.account.domain;

import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.common.exception.CurrencyMismatchException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void shouldDebitAccountWhenActiveAndHasEnoughBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        account.debit(Money.of("200.00", Currency.TRY));
        assertEquals(new BigDecimal("800.00"), account.getBalance().amount());
    }

    @Test
    void shouldCreditAccountWhenActive() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        account.credit(Money.of("500.00", Currency.TRY));
        assertEquals(new BigDecimal("1500.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowInsufficientBalanceExceptionWhenDebitAmountIsGreaterThanBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class, () ->
                account.debit(Money.of("101.00", Currency.TRY)));
        assertEquals("Bakiye yetersiz. Mevcut: 100.00 TRY, İstenen: 101.00 TRY", ex.getMessage());
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenDebitOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.SUSPENDED);
        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class, () ->
                account.debit(Money.of("100.00", Currency.TRY)));
        assertEquals("Hesap aktif değil: TR290006200000000000000111", ex.getMessage());
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenCreditOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.SUSPENDED);
        AccountNotActiveException ex = assertThrows(AccountNotActiveException.class, () ->
                account.credit(Money.of("100.00", Currency.TRY)));
        assertEquals("Hesap aktif değil: TR290006200000000000000111", ex.getMessage());
    }

    @Test
    void shouldThrowWhenIbanIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, null, "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowWhenOwnerNameIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), null, Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowWhenBalanceIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", null, AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, null, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenOwnerNameIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), "   ", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenDebitOrCreditArgIsNull() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertThrows(NullPointerException.class, () -> account.debit(null));
        assertThrows(NullPointerException.class, () -> account.credit(null));
    }

    @Test
    void shouldCreateAccountWithVersion() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE, 5L);
        assertEquals(5L, account.getVersion());
    }

    @Test
    void shouldCreateAccountWithNullVersionByDefault() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNull(account.getVersion());
    }

    @Test
    void shouldThrowWhenDebitAmountExceedsBalanceWithExactBoundary() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        assertDoesNotThrow(() -> account.debit(Money.of("100.00", Currency.TRY)));
        assertEquals(new BigDecimal("0.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenDebitCurrencyMismatchesBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        CurrencyMismatchException ex = assertThrows(CurrencyMismatchException.class, () ->
                account.debit(Money.of("50.00", Currency.USD)));
        assertTrue(ex.getMessage().contains("karşılaştırılamaz"));
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenCreditCurrencyMismatchesBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        CurrencyMismatchException ex = assertThrows(CurrencyMismatchException.class, () ->
                account.credit(Money.of("50.00", Currency.USD)));
        assertTrue(ex.getMessage().contains("toplanamaz"));
    }
}

