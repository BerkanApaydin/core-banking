package com.bank.app.transfer.adapter.in.event;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.TransferCancelledNotificationEvent;
import com.bank.app.transfer.domain.TransferCompletedNotificationEvent;
import com.bank.app.transfer.domain.TransferFailedNotificationEvent;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferEventConsumerTest {

    @Mock private SendNotificationPort email;
    @Mock private SendNotificationPort sms;

    private TransferCompletedNotificationEvent completedEvent() {
        return new TransferCompletedNotificationEvent(1L, 10L, 20L,
                Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
    }

    @Test
    void shouldFanOutCompletedToEveryChannel() {
        new TransferEventConsumer(List.of(email, sms)).handleTransferCompleted(completedEvent());

        verify(email).notifyTransferCompleted(any());
        verify(sms).notifyTransferCompleted(any());
    }

    @Test
    void shouldFanOutCancelledToEveryChannel() {
        TransferCancelledNotificationEvent event = new TransferCancelledNotificationEvent(2L, 10L, 20L,
                Money.of("50.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());

        new TransferEventConsumer(List.of(email, sms)).handleTransferCancelled(event);

        verify(email).notifyTransferCancelled(event);
        verify(sms).notifyTransferCancelled(event);
    }

    @Test
    void shouldFanOutFailedToEveryChannel() {
        TransferFailedNotificationEvent event = new TransferFailedNotificationEvent(4L, 10L, 20L,
                Money.of("25.00", Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());

        new TransferEventConsumer(List.of(email, sms)).handleTransferFailed(event);

        verify(email).notifyTransferFailed(event);
        verify(sms).notifyTransferFailed(event);
    }

    @Test
    void shouldRethrowFirstFailureAfterTryingEveryChannel() {
        RuntimeException first = new RuntimeException("smtp down");
        doThrow(first).when(email).notifyTransferCompleted(any());

        assertThatThrownBy(() -> new TransferEventConsumer(List.of(email, sms))
                .handleTransferCompleted(completedEvent()))
                .isSameAs(first);
        verify(sms).notifyTransferCompleted(any());
    }

    @Test
    void shouldSuppressLaterFailuresIntoTheFirst() {
        RuntimeException first = new RuntimeException("smtp down");
        RuntimeException second = new RuntimeException("sms down");
        doThrow(first).when(email).notifyTransferCancelled(any());
        doThrow(second).when(sms).notifyTransferCancelled(any());
        TransferCancelledNotificationEvent event = new TransferCancelledNotificationEvent(3L, 10L, 20L,
                Money.of("10.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now());

        assertThatThrownBy(() -> new TransferEventConsumer(List.of(email, sms))
                .handleTransferCancelled(event))
                .isSameAs(first)
                .hasSuppressedException(second);
        verify(email).notifyTransferCancelled(any());
    }

    @Test
    void shouldSuppressLaterCompletedFailuresIntoTheFirst() {
        // Mirrors the cancelled-path suppressed test: kills the
        // addSuppressed VoidMethodCall mutant on handleTransferCompleted.
        RuntimeException first = new RuntimeException("smtp down");
        RuntimeException second = new RuntimeException("sms down");
        doThrow(first).when(email).notifyTransferCompleted(any());
        doThrow(second).when(sms).notifyTransferCompleted(any());

        assertThatThrownBy(() -> new TransferEventConsumer(List.of(email, sms))
                .handleTransferCompleted(completedEvent()))
                .isSameAs(first)
                .hasSuppressedException(second);
    }

    @Test
    void shouldSuppressLaterFailedFailuresIntoTheFirst() {
        RuntimeException first = new RuntimeException("smtp down");
        RuntimeException second = new RuntimeException("sms down");
        doThrow(first).when(email).notifyTransferFailed(any());
        doThrow(second).when(sms).notifyTransferFailed(any());
        TransferFailedNotificationEvent event = new TransferFailedNotificationEvent(5L, 10L, 20L,
                Money.of("25.00", Currency.TRY), TransferStatus.FAILED, LocalDateTime.now());

        assertThatThrownBy(() -> new TransferEventConsumer(List.of(email, sms))
                .handleTransferFailed(event))
                .isSameAs(first)
                .hasSuppressedException(second);
        verify(email).notifyTransferFailed(any());
        verify(sms).notifyTransferFailed(any());
    }

    @Test
    void shouldDeliverNothingWhenNoChannelsRegistered() {
        new TransferEventConsumer(List.of()).handleTransferCompleted(completedEvent());
        new TransferEventConsumer(List.of()).handleTransferCancelled(
                new TransferCancelledNotificationEvent(4L, 10L, 20L,
                        Money.of("10.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now()));

        verify(email, never()).notifyTransferCompleted(any());
    }
}
