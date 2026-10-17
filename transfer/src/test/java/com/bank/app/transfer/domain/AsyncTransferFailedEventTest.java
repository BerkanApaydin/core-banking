package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import com.bank.app.common.domain.AccountId;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AsyncTransferFailedEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        LocalDateTime now = LocalDateTime.now();
        AsyncTransferFailedEvent event = new AsyncTransferFailedEvent(1L, 10L, 20L, amount,
                TransferStatus.FAILED, now);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(TransferStatus.FAILED, event.status());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldImplementDomainEvent() {
        AsyncTransferFailedEvent event = new AsyncTransferFailedEvent(1L, 10L, 20L, Money.of("100", Currency.TRY),
                TransferStatus.FAILED, LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldCreateFromTransfer() {
        Transfer transfer = new Transfer(1L, new AccountId(10L), new AccountId(20L), Money.of("500.00", Currency.TRY), TransferStatus.FAILED,
                LocalDateTime.now());
        AsyncTransferFailedEvent event = AsyncTransferFailedEvent.from(transfer);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(Money.of("500.00", Currency.TRY), event.amount());
        assertEquals(TransferStatus.FAILED, event.status());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferFailedEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY),
                        TransferStatus.FAILED, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AsyncTransferFailedEvent(1L, 10L, 20L, null, TransferStatus.FAILED, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferPassedToFromIsNull() {
        assertThrows(NullPointerException.class,
                () -> AsyncTransferFailedEvent.from(null));
    }

    @Test
    void shouldExposeTransferAggregateIdentity() {
        AsyncTransferFailedEvent event = new AsyncTransferFailedEvent(42L, 10L, 20L,
                Money.of("100.00", Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());

        assertEquals("Transfer", event.aggregateType());
        assertEquals("42", event.aggregateId());
    }
}
