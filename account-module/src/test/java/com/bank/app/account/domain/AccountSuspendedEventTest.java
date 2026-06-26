package com.bank.app.account.domain;

import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountSuspendedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        AccountSuspendedEvent event = new AccountSuspendedEvent(1L, now);

        assertEquals(1L, event.accountId());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountSuspendedEvent event = new AccountSuspendedEvent(1L, LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new AccountSuspendedEvent(null, LocalDateTime.now()));
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThrows(NullPointerException.class,
                () -> new AccountSuspendedEvent(1L, null));
    }
}
