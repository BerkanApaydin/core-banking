package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferCancelledEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        Money amount = Money.of("500.00", Currency.TRY);
        TransferCancelledEvent event = new TransferCancelledEvent(1L, 10L, 20L, amount);

        assertEquals(1L, event.getTransferId());
        assertEquals(10L, event.getSenderAccountId());
        assertEquals(20L, event.getReceiverAccountId());
        assertEquals(amount, event.getAmount());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(null, 10L, 20L, Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, null, 20L, Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverAccountIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, null, Money.of("100.00", Currency.TRY)));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        assertThrows(NullPointerException.class,
                () -> new TransferCancelledEvent(1L, 10L, 20L, null));
    }
}
