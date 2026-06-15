package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.application.port.SendNotificationPort;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);
    private final List<SendNotificationPort> notificationPorts;

    public TransferEventListener(List<SendNotificationPort> notificationPorts) {
        this.notificationPorts = notificationPorts;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransferCompleted(TransferCompletedEvent event) {
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCompleted(event.getTransfer());
            } catch (Exception e) {
                log.error("Failed to send notification via {}", port.getClass().getSimpleName(), e);
            }
        }
    }
}
