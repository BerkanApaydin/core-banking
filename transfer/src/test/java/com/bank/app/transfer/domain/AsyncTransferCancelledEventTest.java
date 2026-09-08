package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AsyncTransferCancelledEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        LocalDateTime now = LocalDateTime.now();
        AsyncTransferCancelledEvent event = new AsyncTransferCancelledEvent(1L, 10L, 20L, amount,
                TransferStatus.CANCELLED, now);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(TransferStatus.CANCELLED, event.status());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AsyncTransferCancelledEvent event = new AsyncTransferCancelledEvent(1L, 10L, 20L,
                Money.of("100.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldExposeTransferAggregateIdentity() {
        AsyncTransferCancelledEvent event = new AsyncTransferCancelledEvent(42L, 10L, 20L,
                Money.of("100.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());

        assertEquals("Transfer", event.aggregateType());
        assertEquals("42", event.aggregateId());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferCancelledEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY),
                        TransferStatus.CANCELLED, LocalDateTime.now()));
    }
}
