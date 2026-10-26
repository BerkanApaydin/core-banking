package com.bank.app.transfer.adapter.in.event;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.TransferCancelledNotificationEvent;
import com.bank.app.transfer.domain.TransferCompletedNotificationEvent;
import com.bank.app.transfer.domain.TransferFailedNotificationEvent;
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
    public void handleTransferCompleted(TransferCompletedNotificationEvent event) {
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

    public void handleTransferCancelled(TransferCancelledNotificationEvent event) {
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

    // Called by TransferFailedOutboxRelay. A failed channel leaves the source
    // event retryable instead of acknowledging it before delivery has run.
    public void handleTransferFailed(TransferFailedNotificationEvent event) {
        RuntimeException failure = null;
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferFailed(event);
            } catch (RuntimeException e) {
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
    }
}
