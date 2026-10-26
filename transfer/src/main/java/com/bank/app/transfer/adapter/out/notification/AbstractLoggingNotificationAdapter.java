package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.TransferCancelledNotificationEvent;
import com.bank.app.transfer.domain.TransferCompletedNotificationEvent;
import com.bank.app.transfer.domain.TransferFailedNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Shared logging-only notification behavior. Concrete channels only declare
 * their name; the class-level retry policy is inherited by every channel and
 * applied by Spring Retry's proxy to all delivery methods.
 */
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public abstract class AbstractLoggingNotificationAdapter implements SendNotificationPort {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract String channel();

    @Override
    public void notifyTransferCompleted(TransferCompletedNotificationEvent event) {
        if (event == null) {
            log.warn("{} notification received null event, skipping.", channel());
            return;
        }
        log.info("{} Notification: Transfer {} completed successfully. Amount: {} {}",
                channel(), event.transferId(), event.amount().amount(), event.amount().currency());
    }

    @Override
    public void notifyTransferCancelled(TransferCancelledNotificationEvent event) {
        if (event == null) {
            log.warn("{} cancellation notification received null event, skipping.", channel());
            return;
        }
        log.info("{} Notification: Transfer {} cancelled. Amount: {} {}",
                channel(), event.transferId(), event.amount().amount(), event.amount().currency());
    }

    @Override
    public void notifyTransferFailed(TransferFailedNotificationEvent event) {
        if (event == null) {
            log.warn("{} failure notification received null event, skipping.", channel());
            return;
        }
        log.info("{} Notification: Transfer {} failed. Amount: {} {}",
                channel(), event.transferId(), event.amount().amount(), event.amount().currency());
    }
}
