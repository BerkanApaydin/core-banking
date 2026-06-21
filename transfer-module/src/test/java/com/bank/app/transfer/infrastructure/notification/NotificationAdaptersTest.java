package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAdaptersTest {

    @Mock private Transfer transfer;

    @BeforeEach
    void setUp() {
        lenient().when(transfer.getId()).thenReturn(1L);
        lenient().when(transfer.getSenderAccountId()).thenReturn(10L);
        lenient().when(transfer.getReceiverAccountId()).thenReturn(20L);
        lenient().when(transfer.getAmount()).thenReturn(new Money(BigDecimal.TEN, Currency.TRY));
        lenient().when(transfer.getStatus()).thenReturn(TransferStatus.COMPLETED);
        lenient().when(transfer.getCreatedAt()).thenReturn(LocalDateTime.now());
    }

    @Test
    void shouldNotifyAllPortsOnTransferCompleted() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);

        doThrow(new RuntimeException("failed")).when(port1).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));

        TransferEventListener listener = new TransferEventListener(List.of(port1, port2));
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));

        verify(port1).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));
        verify(port2).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));
    }

    @Test
    void shouldContinueWhenAllPortsFail() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);

        doThrow(new RuntimeException("fail1")).when(port1).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));
        doThrow(new RuntimeException("fail2")).when(port2).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));

        TransferEventListener listener = new TransferEventListener(List.of(port1, port2));
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));

        verify(port1).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));
        verify(port2).notifyTransferCompleted(any(AsyncTransferCompletedEvent.class));
    }

    @Test
    void shouldHandleEmptyPortList() {
        TransferEventListener listener = new TransferEventListener(List.of());
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));
    }
}
