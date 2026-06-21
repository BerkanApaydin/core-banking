package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransferEventListenerTest {

    @Mock
    private SendNotificationPort notificationPort;

    @Test
    void shouldSendNotificationWhenTransferCompletedEventIsTriggered() {
        TransferEventListener listener = new TransferEventListener(List.of(notificationPort));

        Transfer transfer = new Transfer(1L, 100L, 200L, Money.of("100.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        AsyncTransferCompletedEvent event = AsyncTransferCompletedEvent.from(transfer);

        listener.handleTransferCompleted(event);

        verify(notificationPort).notifyTransferCompleted(AsyncTransferCompletedEvent.from(transfer));
    }
}
