package com.bank.app.account.domain;

import com.bank.app.common.exception.AccountNotActiveException;
import com.bank.app.common.exception.InsufficientBalanceException;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    @Test
    void shouldDebitAccountWhenActiveAndHasEnoughBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        account.debit(Money.of("200.00", Money.Currency.TRY));
        assertEquals(new BigDecimal("800.00"), account.getBalance().amount());
    }

    @Test
    void shouldCreditAccountWhenActive() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        account.credit(Money.of("500.00", Money.Currency.TRY));
        assertEquals(new BigDecimal("1500.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowInsufficientBalanceExceptionWhenDebitAmountIsGreaterThanBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Money.Currency.TRY), true);
        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class, () ->
                account.debit(Money.of("101.00", Money.Currency.TRY)));
        assertTrue(ex.getMessage().contains("yetersiz"));
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenDebitOrCreditOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), false);
        AccountNotActiveException ex1 = assertThrows(AccountNotActiveException.class, () ->
                account.debit(Money.of("100.00", Money.Currency.TRY)));
        assertTrue(ex1.getMessage().contains("aktif"));
        AccountNotActiveException ex2 = assertThrows(AccountNotActiveException.class, () ->
                account.credit(Money.of("100.00", Money.Currency.TRY)));
        assertTrue(ex2.getMessage().contains("aktif"));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenConstructorArgsAreNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, null, "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true));
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), null, Money.of("1000.00", Money.Currency.TRY), true));
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", null, true));
        assertThrows(NullPointerException.class, () ->
                new Account(1L, null, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenOwnerNameIsEmpty() {
        assertThrows(IllegalArgumentException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), "   ", Money.of("1000.00", Money.Currency.TRY), true));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenDebitOrCreditArgIsNull() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertThrows(NullPointerException.class, () -> account.debit(null));
        assertThrows(NullPointerException.class, () -> account.credit(null));
    }

    @Test
    void shouldCreateAccountWithVersion() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true, 5L);
        assertEquals(5L, account.getVersion());
    }

    @Test
    void shouldCreateAccountWithNullVersionByDefault() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertNull(account.getVersion());
    }

    @Test
    void shouldThrowWhenUserIdIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, null, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true));
    }

    @Test
    void shouldThrowWhenDebitAmountExceedsBalanceWithExactBoundary() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("100.00", Money.Currency.TRY), true);
        assertDoesNotThrow(() -> account.debit(Money.of("100.00", Money.Currency.TRY)));
        assertEquals(new BigDecimal("0.00"), account.getBalance().amount());
    }
}

