package com.bank.app.transfer.adapter.out.notification;

import com.bank.app.transfer.application.port.out.SendNotificationPort;
import com.bank.app.transfer.domain.AsyncTransferCancelledEvent;
import com.bank.app.transfer.domain.AsyncTransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared logging-only notification behavior. Concrete channels only declare their name;
 * delivery (retry policy, transport) is configured on the overriding methods so that
 * Spring AOP proxies keep working.
 */
public abstract class AbstractLoggingNotificationAdapter implements SendNotificationPort {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract String channel();

    @Override
    public void notifyTransferCompleted(AsyncTransferCompletedEvent event) {
        if (event == null) {
            log.warn("{} notification received null event, skipping.", channel());
            return;
        }
        log.info("{} Notification: Transfer {} completed successfully. Amount: {} {}",
                channel(), event.transferId(), event.amount().amount(), event.amount().currency());
    }

    @Override
    public void notifyTransferCancelled(AsyncTransferCancelledEvent event) {
        if (event == null) {
            log.warn("{} cancellation notification received null event, skipping.", channel());
            return;
        }
        log.info("{} Notification: Transfer {} cancelled. Amount: {} {}",
                channel(), event.transferId(), event.amount().amount(), event.amount().currency());
    }
}
