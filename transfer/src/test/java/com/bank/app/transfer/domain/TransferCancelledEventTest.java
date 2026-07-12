package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferCancelledEventTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 6, 15, 10, 30);
    private static final TransferStatus STATUS = TransferStatus.CANCELLED;

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        TransferCancelledEvent event = new TransferCancelledEvent(1L, 10L, 20L, amount, STATUS, FIXED_TIME);

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(STATUS, event.status());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY), STATUS, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, null, 20L, Money.of("100.00", Currency.TRY), STATUS, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, null, Money.of("100.00", Currency.TRY), STATUS, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, null, STATUS, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenStatusIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, Money.of("100.00", Currency.TRY), null, FIXED_TIME));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenOccurredAtIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, Money.of("100.00", Currency.TRY), STATUS, null));
    }
}
