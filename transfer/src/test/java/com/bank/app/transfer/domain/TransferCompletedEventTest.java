package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferCompletedEventTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        TransferCompletedEvent event = new TransferCompletedEvent(1L, 10L, 20L, amount, TransferStatus.COMPLETED, FIXED_TIME);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(TransferStatus.COMPLETED, event.status());
        assertEquals(FIXED_TIME, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        TransferCompletedEvent event = new TransferCompletedEvent(1L, 10L, 20L, Money.of("100", Currency.TRY), TransferStatus.COMPLETED, FIXED_TIME);
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCompletedEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCompletedEvent(1L, null, 20L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCompletedEvent(1L, 10L, 20L, null, TransferStatus.COMPLETED, FIXED_TIME));
    }
}
