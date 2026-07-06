package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class SmsNotificationAdapter implements SendNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationAdapter.class);

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        if (event == null) {
            log.warn("SMS notification received null event, skipping.");
            return;
        }
        log.info("SMS Notification: Transfer {} completed successfully. Amount: {} {}",
                event.transferId(), event.amount().amount(), event.amount().currency());
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void notifyTransferCancelled(AsyncTransferCancelledEvent event) {
        if (event == null) {
            log.warn("SMS cancellation notification received null event, skipping.");
            return;
        }
        log.info("SMS Notification: Transfer {} cancelled. Amount: {} {}",
                event.transferId(), event.amount().amount(), event.amount().currency());
    }
}
