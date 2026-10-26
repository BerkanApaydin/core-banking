package com.bank.app.transfer.adapter.out.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulation SMS channel: logs instead of delivering. See
 * {@link EmailNotificationAdapter} for the replacement contract.
 */
@Component
@ConditionalOnProperty(name = "app.notification.sms-enabled", havingValue = "true", matchIfMissing = true)
public class SmsNotificationAdapter extends AbstractLoggingNotificationAdapter {

    @Override
    protected String channel() {
        return "SMS";
    }
}
