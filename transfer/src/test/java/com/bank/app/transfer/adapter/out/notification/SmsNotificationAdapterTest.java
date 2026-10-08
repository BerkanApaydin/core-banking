package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SuppressWarnings("null")
class SmsNotificationAdapterTest {

    private final SmsNotificationAdapter adapter = new SmsNotificationAdapter();

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

    @Test
    void shouldExposeSmsChannel() {
        assertThat(adapter.channel()).isEqualTo("SMS");
    }

    @Test
    void shouldLogCompletedNotificationWithChannel() {
        Logger logger =
                (Logger) LoggerFactory.getLogger(SmsNotificationAdapter.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Transfer transfer = new Transfer(7L, 10L, 20L,
                    Money.of("100.00", Currency.TRY),
                    TransferStatus.COMPLETED, LocalDateTime.now());
            adapter.notifyTransferCompleted(AsyncTransferCompletedEvent.from(transfer));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list)
                .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("SMS").contains("7"));
    }

    @Test
    void shouldLogCancelledNotificationWithChannel() {
        Logger logger =
                (Logger) LoggerFactory.getLogger(SmsNotificationAdapter.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            adapter.notifyTransferCancelled(new AsyncTransferCancelledEvent(
                    9L, 10L, 20L, Money.of("50.00", Currency.TRY),
                    TransferStatus.CANCELLED, LocalDateTime.now()));
        } finally {
            logger.detachAppender(appender);
        }
        assertThat(appender.list)
                .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("SMS").contains("9"));
    }
}
