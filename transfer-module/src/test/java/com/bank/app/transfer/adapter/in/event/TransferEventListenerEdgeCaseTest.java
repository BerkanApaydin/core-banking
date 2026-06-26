package com.bank.app.transfer.adapter.in.event;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferEventListenerEdgeCaseTest {

    @Mock
    private SendNotificationPort notificationPort;

    @Test
    void shouldHandleNotificationFailureGracefully() {
        TransferEventListener listener = new TransferEventListener(List.of(notificationPort));

        Transfer transfer = new Transfer(1L, 100L, 200L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        doThrow(new RuntimeException("Service unavailable"))
                .when(notificationPort).notifyTransferCompleted(event);

        listener.handleTransferCompleted(event);

        verify(notificationPort).notifyTransferCompleted(event);
    }

    @Test
    void shouldHandleMultipleNotificationPorts() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);
        TransferEventListener listener = new TransferEventListener(List.of(port1, port2));

        Transfer transfer = new Transfer(1L, 100L, 200L, Money.of("100.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        listener.handleTransferCompleted(event);

        verify(port1).notifyTransferCompleted(event);
        verify(port2).notifyTransferCompleted(event);
    }
}
