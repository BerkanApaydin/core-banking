package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.account.domain.exception.AccountNotActiveException;
import com.bank.app.account.domain.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountEdgeCaseTest {

    @Test
    void shouldDebitExactBalanceToZero() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        account.debit(Money.of("100.00", Currency.TRY));
        assertEquals(BigDecimal.ZERO.setScale(2), account.getBalance().amount());
    }

    @Test
    void shouldHandleMultipleConsecutiveDebits() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        account.debit(Money.of("100.00", Currency.TRY));
        account.debit(Money.of("200.00", Currency.TRY));
        account.debit(Money.of("50.50", Currency.TRY));
        assertEquals(new BigDecimal("649.50"), account.getBalance().amount());
    }

    @Test
    void shouldHandleMultipleConsecutiveCredits() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        account.credit(Money.of("100.00", Currency.TRY));
        account.credit(Money.of("200.00", Currency.TRY));
        account.credit(Money.of("50.50", Currency.TRY));
        assertEquals(new BigDecimal("1350.50"), account.getBalance().amount());
    }

    @Test
    void shouldHandleCreditAndDebitSequence() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("500.00", Currency.TRY), AccountStatus.ACTIVE);
        account.credit(Money.of("200.00", Currency.TRY));
        account.debit(Money.of("100.00", Currency.TRY));
        account.credit(Money.of("50.00", Currency.TRY));
        assertEquals(new BigDecimal("650.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowInsufficientBalanceWhenDebitExceedsBalance() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("100.00", Currency.TRY), AccountStatus.ACTIVE);
        assertThrows(InsufficientBalanceException.class,
                () -> account.debit(Money.of("100.01", Currency.TRY)));
    }

    @Test
    void shouldThrowWhenDebitOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.SUSPENDED);
        assertThrows(AccountNotActiveException.class,
                () -> account.debit(Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowWhenCreditOnInactiveAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.SUSPENDED);
        assertThrows(AccountNotActiveException.class,
                () -> account.credit(Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowWhenOwnerNameIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                        "   ", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
        assertEquals("Sahip adı boş olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowWhenOwnerNameIsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                        "", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldCreateAccountWithNullId() {
        Account account = new Account(null, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNull(account.getId());
    }

    @Test
    void shouldCreateAccountWithNullVersion() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNull(account.getVersion());
    }

    @Test
    void shouldPreserveOriginalBalanceAfterOperation() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        account.debit(Money.of("100.00", Currency.TRY));
        assertEquals(new BigDecimal("900.00"), account.getBalance().amount());
    }

    @Test
    void shouldThrowWhenDebitWithNullAmount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertThrows(NullPointerException.class, () -> account.debit(null));
    }

    @Test
    void shouldThrowWhenCreditWithNullAmount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertThrows(NullPointerException.class, () -> account.credit(null));
    }

    @Test
    void shouldThrowWhenUserIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, null, new Iban("TR290006200000000000000111"),
                        "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE));
    }

    @Test
    void shouldThrowWhenUserIdIsNullOnConstructorWithVersion() {
        assertThrows(NullPointerException.class,
                () -> new Account(1L, null, new Iban("TR290006200000000000000111"),
                        "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE, 1L));
    }

    @Test
    void shouldReturnCorrectUserId() {
        Account account = new Account(1L, 42L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertEquals(42L, account.getUserId());
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenDebitOnClosedAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.CLOSED);
        assertThrows(AccountNotActiveException.class,
                () -> account.debit(Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenCreditOnClosedAccount() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"),
                "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.CLOSED);
        assertThrows(AccountNotActiveException.class,
                () -> account.credit(Money.of("100.00", Currency.TRY)));
    }
}
