package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulation SMS channel: logs instead of delivering. See
 * {@link EmailNotificationAdapter} for the replacement contract.
 */
@Component
@ConditionalOnProperty(name = "app.notification.sms-enabled", havingValue = "true", matchIfMissing = true)
public class SmsNotificationAdapter extends AbstractLoggingNotificationAdapter
        implements SendNotificationPort {

    @Override
    protected String channel() {
        return "SMS";
    }
}
