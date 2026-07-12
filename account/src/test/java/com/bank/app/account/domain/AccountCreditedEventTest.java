package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountCreditedEventTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("100.00", Currency.TRY);
        Money newBalance = Money.of("500.00", Currency.TRY);
        AccountCreditedEvent event = new AccountCreditedEvent(1L, amount, newBalance, FIXED_TIME);

        assertEquals(1L, event.accountId());
        assertEquals(amount, event.amount());
        assertEquals(newBalance, event.newBalance());
        assertEquals(FIXED_TIME, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountCreditedEvent event = new AccountCreditedEvent(1L, Money.of("100", Currency.TRY),
                Money.of("500", Currency.TRY), FIXED_TIME);
        assertInstanceOf(DomainEvent.class, event);
    }
}
