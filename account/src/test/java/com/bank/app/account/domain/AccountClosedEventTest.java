package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountClosedEventTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Test
    void shouldCreateEventWithAllFields() {
        Money finalBalance = Money.of("0.00", Currency.TRY);
        AccountClosedEvent event = new AccountClosedEvent(1L, finalBalance, FIXED_TIME);

        assertEquals(1L, event.accountId());
        assertEquals(finalBalance, event.finalBalance());
        assertEquals(FIXED_TIME, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountClosedEvent event = new AccountClosedEvent(1L, Money.of("0", Currency.TRY), FIXED_TIME);
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(null, Money.of("0", Currency.TRY), FIXED_TIME));
    }

    @Test
    void shouldRejectNullFinalBalance() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(1L, null, FIXED_TIME));
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(1L, Money.of("0", Currency.TRY), null));
    }
}
