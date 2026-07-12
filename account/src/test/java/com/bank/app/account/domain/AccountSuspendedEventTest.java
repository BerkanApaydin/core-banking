package com.bank.app.account.domain;

import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountSuspendedEventTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Test
    void shouldCreateEventWithAllFields() {
        AccountSuspendedEvent event = new AccountSuspendedEvent(1L, FIXED_TIME);

        assertEquals(1L, event.accountId());
        assertEquals(FIXED_TIME, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountSuspendedEvent event = new AccountSuspendedEvent(1L, FIXED_TIME);
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldRejectNullAccountId() {
        assertThrows(NullPointerException.class,
                () -> new AccountSuspendedEvent(null, FIXED_TIME));
    }

    @Test
    void shouldRejectNullOccurredAt() {
        assertThrows(NullPointerException.class,
                () -> new AccountSuspendedEvent(1L, null));
    }
}
