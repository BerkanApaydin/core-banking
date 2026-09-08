package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings("null")
class EmailNotificationAdapterTest {

    private final EmailNotificationAdapter adapter = new EmailNotificationAdapter();

    @Test
    void shouldLogSuccessfullyOnNotify() {
        Transfer transfer = new Transfer(1L, 10L, 20L,
                Money.of("100.00", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());

        assertDoesNotThrow(() -> adapter.notifyTransferCompleted(AsyncTransferCompletedEvent.from(transfer)));
    }

    @Test
    void shouldHandleNullTransferGracefully() {
        assertDoesNotThrow(() -> adapter.notifyTransferCompleted(null));
    }

    @Test
    void shouldLogSuccessfullyOnCancelNotify() {
        AsyncTransferCancelledEvent event = new AsyncTransferCancelledEvent(
                1L, 10L, 20L, Money.of("100.00", Currency.TRY),
                TransferStatus.CANCELLED, LocalDateTime.now());

        assertDoesNotThrow(() -> adapter.notifyTransferCancelled(event));
    }

    @Test
    void shouldHandleNullCancelGracefully() {
        assertDoesNotThrow(() -> adapter.notifyTransferCancelled(null));
    }
}
