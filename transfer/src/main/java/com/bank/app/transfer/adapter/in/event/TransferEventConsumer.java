package com.bank.app.transfer.adapter.in.event;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);
    private final List<SendNotificationPort> notificationPorts;

    public TransferEventConsumer(List<SendNotificationPort> notificationPorts) {
        this.notificationPorts = notificationPorts;
    }

    // Async: notifications are fire-and-forget side effects. Without this, the
    // AFTER_COMMIT listener (including @Retryable backoffs on the adapters)
    // blocks the request thread of the transfer/cancel call.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransferCompleted(AsyncTransferCompletedEvent event) {
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCompleted(event);
            } catch (Exception e) {
                log.error("Failed to send notification via {}", port.getClass().getSimpleName(), e);
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransferCancelled(AsyncTransferCancelledEvent event) {
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCancelled(event);
            } catch (Exception e) {
                log.error("Failed to send cancellation notification via {}", port.getClass().getSimpleName(), e);
            }
        }
    }
}
