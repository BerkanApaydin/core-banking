package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.AccountNotActiveException;
import com.bank.app.common.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountEdgeCaseTest {

    @Test
    void shouldDebitExactBalanceToZero() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("100.00", Money.Currency.TRY), true);
        account.debit(Money.of("100.00", Money.Currency.TRY));
        assertEquals(BigDecimal.ZERO.setScale(2), account.getBalance().amount());
    }

    @Test
    void shouldHandleMultipleConsecutiveDebits() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        account.debit(Money.of("100.00", Money.Currency.TRY));
        account.debit(Money.of("200.00", Money.Currency.TRY));
        account.debit(Money.of("50.50", Money.Currency.TRY));
        assertEquals(new BigDecimal("649.50"), account.getBalance().amount());
    }

    @Test
    void shouldHandleMultipleConsecutiveCredits() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        account.credit(Money.of("100.00", Money.Currency.TRY));
        account.credit(Money.of("200.00", Money.Currency.TRY));
        account.credit(Money.of("50.50", Money.Currency.TRY));
        assertEquals(new BigDecimal("1350.50"), account.getBalance().amount());
    }

    @Test
    void shouldHandleCreditAndDebitSequence() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("500.00", Money.Currency.TRY), true);
        account.credit(Money.of("200.00", Money.Currency.TRY));
        account.debit(Money.of("100.00", Money.Currency.TRY));
        account.credit(Money.of("50.00", Money.Currency.TRY));
        assertEquals(new BigDecimal("650.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowInsufficientBalanceWhenDebitExceedsBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("100.00", Money.Currency.TRY), true);
        assertThrows(InsufficientBalanceException.class,
                () -> account.debit(Money.of("100.01", Money.Currency.TRY)));
    }

    @Test
    void shouldThrowWhenDebitOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), false);
        assertThrows(AccountNotActiveException.class,
                () -> account.debit(Money.of("100.00", Money.Currency.TRY)));
    }

    @Test
    void shouldThrowWhenCreditOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), false);
        assertThrows(AccountNotActiveException.class,
                () -> account.credit(Money.of("100.00", Money.Currency.TRY)));
    }

    @Test
    void shouldThrowWhenOwnerNameIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                        "   ", Money.of("1000.00", Money.Currency.TRY), true));
        assertEquals("Sahip adı boş olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowWhenOwnerNameIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                        "", Money.of("1000.00", Money.Currency.TRY), true));
    }

    @Test
    void shouldCreateAccountWithNullId() {
        Account account = new Account(null, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertNull(account.getId());
    }

    @Test
    void shouldCreateAccountWithNullVersion() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertNull(account.getVersion());
    }

    @Test
    void shouldPreserveOriginalBalanceAfterOperation() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        account.debit(Money.of("100.00", Money.Currency.TRY));
        assertEquals(new BigDecimal("900.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowWhenDebitWithNullAmount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertThrows(NullPointerException.class, () -> account.debit(null));
    }

    @Test
    void shouldThrowWhenCreditWithNullAmount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertThrows(NullPointerException.class, () -> account.credit(null));
    }

    @Test
    void shouldThrowWhenUserIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, null, new Iban("TR290006200000000000000111"),
                        "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true));
    }

    @Test
    void shouldThrowWhenUserIdIsNullOnConstructorWithVersion() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, null, new Iban("TR290006200000000000000111"),
                        "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true, 1L));
    }

    @Test
    void shouldReturnCorrectUserId() {
        Account account = new Account(1L, 42L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Money.Currency.TRY), true);
        assertEquals(42L, account.getUserId());
    }
}
