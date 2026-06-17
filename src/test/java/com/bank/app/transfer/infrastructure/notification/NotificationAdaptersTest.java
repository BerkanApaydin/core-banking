package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.application.port.SendNotificationPort;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationAdaptersTest {

    @Test
    void testEmailNotificationAdapter() {
        EmailNotificationAdapter adapter = new EmailNotificationAdapter();
        Transfer transfer = mock(Transfer.class);
        Money money = new Money(BigDecimal.TEN, Money.Currency.TRY);
        when(transfer.getId()).thenReturn(1L);
        when(transfer.getAmount()).thenReturn(money);

        assertDoesNotThrow(() -> adapter.notifyTransferCompleted(transfer));
        assertDoesNotThrow(() -> adapter.fallbackNotify(transfer, new RuntimeException("connection error")));
    }

    @Test
    void testSmsNotificationAdapter() {
        SmsNotificationAdapter adapter = new SmsNotificationAdapter();
        Transfer transfer = mock(Transfer.class);
        Money money = new Money(BigDecimal.TEN, Money.Currency.TRY);
        when(transfer.getId()).thenReturn(1L);
        when(transfer.getAmount()).thenReturn(money);

        assertDoesNotThrow(() -> adapter.notifyTransferCompleted(transfer));
        assertDoesNotThrow(() -> adapter.fallbackNotify(transfer, new RuntimeException("connection error")));
    }

    @Test
    void testTransferEventListener() {
        SendNotificationPort port1 = mock(SendNotificationPort.class);
        SendNotificationPort port2 = mock(SendNotificationPort.class);

        Transfer transfer = mock(Transfer.class);
        doThrow(new RuntimeException("failed")).when(port1).notifyTransferCompleted(transfer);

        List<SendNotificationPort> ports = new ArrayList<>();
        ports.add(port1);
        ports.add(port2);

        TransferEventListener listener = new TransferEventListener(ports);
        AsyncTransferCompletedEvent event = new AsyncTransferCompletedEvent(transfer);
        assertEquals(transfer, event.transfer());

        assertDoesNotThrow(() -> listener.handleTransferCompleted(event));

        verify(port1).notifyTransferCompleted(transfer);
        verify(port2).notifyTransferCompleted(transfer);
    }
}
