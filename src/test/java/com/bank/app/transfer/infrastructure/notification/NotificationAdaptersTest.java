package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.application.port.SendNotificationPort;
import com.bank.app.common.domain.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAdaptersTest {

    @Mock private Transfer transfer;

    @BeforeEach
    void setUp() {
        lenient().when(transfer.getId()).thenReturn(1L);
        lenient().when(transfer.getAmount()).thenReturn(new Money(BigDecimal.TEN, Money.Currency.TRY));
    }

    @Test
    void shouldNotifyAllPortsOnTransferCompleted() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);

        doThrow(new RuntimeException("failed")).when(port1).notifyTransferCompleted(transfer);

        TransferEventListener listener = new TransferEventListener(List.of(port1, port2));
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));

        verify(port1).notifyTransferCompleted(transfer);
        verify(port2).notifyTransferCompleted(transfer);
    }

    @Test
    void shouldContinueWhenAllPortsFail() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);

        doThrow(new RuntimeException("fail1")).when(port1).notifyTransferCompleted(transfer);
        doThrow(new RuntimeException("fail2")).when(port2).notifyTransferCompleted(transfer);

        TransferEventListener listener = new TransferEventListener(List.of(port1, port2));
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));

        verify(port1).notifyTransferCompleted(transfer);
        verify(port2).notifyTransferCompleted(transfer);
    }

    @Test
    void shouldHandleEmptyPortList() {
        TransferEventListener listener = new TransferEventListener(List.of());
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(transfer);

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));
    }
}
