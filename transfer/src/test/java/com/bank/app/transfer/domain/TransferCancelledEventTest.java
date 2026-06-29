package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferCancelledEventTest {

    private static final TransferStatus STATUS = TransferStatus.CANCELLED;

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        TransferCancelledEvent event = new TransferCancelledEvent(1L, 10L, 20L, amount, STATUS, LocalDateTime.now());

        assertEquals(1L, event.transferId());
        assertEquals(10L, event.senderAccountId());
        assertEquals(20L, event.receiverAccountId());
        assertEquals(amount, event.amount());
        assertEquals(STATUS, event.status());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY), STATUS, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, null, 20L, Money.of("100.00", Currency.TRY), STATUS, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, null, Money.of("100.00", Currency.TRY), STATUS, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, null, STATUS, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenStatusIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, Money.of("100.00", Currency.TRY), null, LocalDateTime.now()));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenOccurredAtIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, Money.of("100.00", Currency.TRY), STATUS, null));
    }
}
