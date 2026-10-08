package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulation SMS channel: logs instead of delivering. See
 * {@link EmailNotificationAdapter} for the replacement contract.
 */
@Component
@ConditionalOnProperty(name = "app.notification.sms-enabled", havingValue = "true", matchIfMissing = true)
public class SmsNotificationAdapter extends AbstractLoggingNotificationAdapter implements SendNotificationPort {

    @Override
    protected String channel() {
        return "SMS";
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        super.notifyTransferCompleted(event);
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void notifyTransferCancelled(AsyncTransferCancelledEvent event) {
        super.notifyTransferCancelled(event);
    }
}
