package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@Primary
public class EmailNotificationAdapter implements SendNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);

    @Override
    @Retryable(maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2))
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        if (event == null) {
            log.warn("Email notification received null event, skipping.");
            return;
        }
        log.info("Email Notification: Transfer {} completed successfully. Amount: {} {}",
                event.transferId(), event.amount().amount(), event.amount().currency());
    }
}
