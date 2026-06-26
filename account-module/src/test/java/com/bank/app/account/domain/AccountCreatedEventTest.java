package com.bank.app.account.domain;

import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountCreatedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money balance = Money.of("1000.00", Currency.TRY);
        AccountCreatedEvent event = new AccountCreatedEvent(1L, new UserId(10L), new Iban("TR290006200000000000000123"), "Test User", balance, LocalDateTime.now());

        assertEquals(1L, event.accountId());
        assertEquals(10L, event.userId().value());
        assertEquals("TR290006200000000000000123", event.iban().value());
        assertEquals("Test User", event.ownerName());
        assertEquals(balance, event.balance());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(null, new UserId(10L), new Iban("TR290006200000000000000123"), "User", Money.of("100.00", Currency.TRY), LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, null, new Iban("TR290006200000000000000123"), "User", Money.of("100.00", Currency.TRY), LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenIbanIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, new UserId(10L), null, "User", Money.of("100.00", Currency.TRY), LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenOwnerNameIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, new UserId(10L), new Iban("TR290006200000000000000123"), null, Money.of("100.00", Currency.TRY), LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenBalanceIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, new UserId(10L), new Iban("TR290006200000000000000123"), "User", null, LocalDateTime.now()));
    }
}
