package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountClosedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money finalBalance = Money.of("0.00", Currency.TRY);
        LocalDateTime now = LocalDateTime.now();
        AccountClosedEvent event = new AccountClosedEvent(1L, finalBalance, now);

        assertEquals(1L, event.accountId());
        assertEquals(finalBalance, event.finalBalance());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountClosedEvent event = new AccountClosedEvent(1L, Money.of("0", Currency.TRY), LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(null, Money.of("0", Currency.TRY), LocalDateTime.now()));
    }

    @Test
    void shouldRejectNullFinalBalance() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(1L, null, LocalDateTime.now()));
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThrows(NullPointerException.class,
                () -> new AccountClosedEvent(1L, Money.of("0", Currency.TRY), null));
    }
}
