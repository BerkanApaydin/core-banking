package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AsyncTransferCompletedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        LocalDateTime now = LocalDateTime.now();
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(1L, 10L, 20L, amount,
                TransferStatus.PENDING, now);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(TransferStatus.PENDING, event.status());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(1L, 10L, 20L, Money.of("100", Currency.TRY),
                TransferStatus.PENDING, LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldCreateFromTransfer() {
        Transfer transfer = new Transfer(1L, 10L, 20L, Money.of("500.00", Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(Money.of("500.00", Currency.TRY), event.amount());
        assertEquals(TransferStatus.COMPLETED, event.status());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferCompletedEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY),
                        TransferStatus.PENDING, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferCompletedEvent(1L, null, 20L, Money.of("100.00", Currency.TRY),
                        TransferStatus.PENDING, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferCompletedEvent(1L, 10L, 20L, null, TransferStatus.PENDING, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferPassedToFromIsNull() {
        assertThrows(NullPointerException.class,
                () -> AsyncTransferCompletedEvent.from(null));
    }
}
