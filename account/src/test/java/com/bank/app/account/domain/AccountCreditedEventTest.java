package com.bank.app.account.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountCreditedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("100.00", Currency.TRY);
        Money newBalance = Money.of("500.00", Currency.TRY);
        LocalDateTime now = LocalDateTime.now();
        AccountCreditedEvent event = new AccountCreditedEvent(1L, amount, newBalance, now);

        assertEquals(1L, event.accountId());
        assertEquals(amount, event.amount());
        assertEquals(newBalance, event.newBalance());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AccountCreditedEvent event = new AccountCreditedEvent(1L, Money.of("100", Currency.TRY),
                Money.of("500", Currency.TRY), LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }
}
