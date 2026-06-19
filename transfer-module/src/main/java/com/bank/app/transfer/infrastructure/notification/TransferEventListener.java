package com.bank.app.transfer.infrastructure.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransferEventListener.class);
    private final List<SendNotificationPort> notificationPorts;

    public TransferEventListener(List<SendNotificationPort> notificationPorts) {
        this.notificationPorts = notificationPorts;
    }

    @EventListener
    public void handleTransferCompleted(AsyncTransferCompletedEvent event) {
        for (SendNotificationPort port : notificationPorts) {
            try {
                port.notifyTransferCompleted(event.transfer());
            } catch (Exception e) {
                log.error("Failed to send notification via {}", port.getClass().getSimpleName(), e);
            }
        }
    }
}
