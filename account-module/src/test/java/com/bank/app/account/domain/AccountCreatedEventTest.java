package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountCreatedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money balance = Money.of("1000.00", Currency.TRY);
        AccountCreatedEvent event = new AccountCreatedEvent(1L, 10L, "TR123456", "Test User", balance);

        assertEquals(1L, event.getAccountId());
        assertEquals(10L, event.getUserId());
        assertEquals("TR123456", event.getIban());
        assertEquals("Test User", event.getOwnerName());
        assertEquals(balance, event.getBalance());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(null, 10L, "TR123", "User", Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenUserIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, null, "TR123", "User", Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenIbanIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, 10L, null, "User", Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenOwnerNameIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, 10L, "TR123", null, Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenBalanceIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AccountCreatedEvent(1L, 10L, "TR123", "User", null));
    }
}
