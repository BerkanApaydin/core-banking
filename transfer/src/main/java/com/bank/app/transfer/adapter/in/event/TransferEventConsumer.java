package com.bank.app.transfer.adapter.in.event;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferEventConsumer {

    private final List<SendNotificationPort> notificationPorts;

    public TransferEventConsumer(List<SendNotificationPort> notificationPorts) {
        this.notificationPorts = notificationPorts;
    }

    // Called by the outbox worker. A failed channel leaves the source event
    // retryable instead of acknowledging it before delivery has run.
    public void handleTransferCompleted(AsyncTransferCompletedEvent event) {
        RuntimeException failure = null;
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCompleted(event);
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }

    public void handleTransferCancelled(AsyncTransferCancelledEvent event) {
        RuntimeException failure = null;
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCancelled(event);
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }
}
