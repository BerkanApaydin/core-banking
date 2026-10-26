package com.bank.app.transfer.adapter.out.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulation email channel: logs instead of delivering. Dispatched via the
 * transactional outbox ({@code TransferEventConsumer}), so a future real
 * provider only replaces this bean — the retry/outbox contract stays.
 */
@Component
@ConditionalOnProperty(name = "app.notification.email-enabled", havingValue = "true", matchIfMissing = true)
public class EmailNotificationAdapter extends AbstractLoggingNotificationAdapter {

    @Override
    protected String channel() {
        return "Email";
    }
}
