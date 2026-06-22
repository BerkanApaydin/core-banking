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

    @Test
    void shouldThrowWhenStatusIsNull() {
        assertThrows(NullPointerException.class, () ->
                new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), null));
    }

    @Test
    void shouldCheckIsActiveReturnsTrueForActive() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertTrue(account.isActive());
    }

    @Test
    void shouldCheckIsActiveReturnsFalseForSuspended() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.SUSPENDED);
        assertFalse(account.isActive());
    }

    @Test
    void shouldCheckIsActiveReturnsFalseForClosed() {
        Account account = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet Yılmaz", Money.of("1000.00", Currency.TRY), AccountStatus.CLOSED);
        assertFalse(account.isActive());
    }

    @Test
    void equalsShouldReturnTrueWhenSameId() {
        Account a1 = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        Account a2 = new Account(1L, 2L, new Iban("TR290006200000000000000222"), "Mehmet", Money.of("500.00", Currency.TRY), AccountStatus.SUSPENDED);
        assertEquals(a1, a2);
    }

    @Test
    void equalsShouldReturnFalseWhenDifferentId() {
        Account a1 = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        Account a2 = new Account(2L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNotEquals(a1, a2);
    }

    @Test
    void equalsShouldReturnFalseWhenBothNullIds() {
        Account a1 = new Account(null, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        Account a2 = new Account(null, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNotEquals(a1, a2);
    }

    @Test
    void equalsShouldReturnFalseWhenOtherObject() {
        Account a1 = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNotEquals("some-string", a1);
    }

    @Test
    void hashCodeShouldBeConsistentWithEquals() {
        Account a1 = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        Account a2 = new Account(1L, 2L, new Iban("TR290006200000000000000222"), "Mehmet", Money.of("500.00", Currency.TRY), AccountStatus.SUSPENDED);
        assertEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void hashCodeShouldBeDifferentForDifferentIds() {
        Account a1 = new Account(1L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        Account a2 = new Account(2L, 1L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        assertNotEquals(a1.hashCode(), a2.hashCode());
    }

    @Test
    void builderShouldBuildAccountWithAllFields() {
        Account account = Account.builder()
                .id(1L)
                .userId(100L)
                .iban(new Iban("TR290006200000000000000111"))
                .ownerName("Ahmet")
                .balance(Money.of("1000.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .version(3L)
                .build();

        assertEquals(1L, account.getId());
        assertEquals(100L, account.getUserId());
        assertEquals("Ahmet", account.getOwnerName());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(3L, account.getVersion());
    }

    @Test
    void builderShouldBuildAccountWithNullVersion() {
        Account account = Account.builder()
                .id(1L)
                .userId(100L)
                .iban(new Iban("TR290006200000000000000111"))
                .ownerName("Ahmet")
                .balance(Money.of("1000.00", Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();

        assertNull(account.getVersion());
    }
}

