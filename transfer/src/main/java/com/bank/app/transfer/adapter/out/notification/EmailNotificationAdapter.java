package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationAdapter extends AbstractLoggingNotificationAdapter implements SendNotificationPort {

    @Override
    protected String channel() {
        return "Email";
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
