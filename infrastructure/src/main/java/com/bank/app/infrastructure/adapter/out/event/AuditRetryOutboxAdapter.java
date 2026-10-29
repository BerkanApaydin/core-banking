package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.AuditEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable fallback for best-effort audit writes.
 *
 * <p>Runs in its own transaction so an aborted caller transaction cannot
 * discard the retry entry. The actual audit row is written by
 * {@code AuditRetryOutboxRelay} after the outbox row commits.
 */
@Component
public class AuditRetryOutboxAdapter {

    private final EventPublisherPort eventPublisherPort;

    public AuditRetryOutboxAdapter(EventPublisherPort eventPublisherPort) {
        this.eventPublisherPort = eventPublisherPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW,
            timeoutString = "${app.transaction.timeout-seconds:30}")
    public void enqueue(AuditEvent event) {
        eventPublisherPort.publish(event);
    }
}
