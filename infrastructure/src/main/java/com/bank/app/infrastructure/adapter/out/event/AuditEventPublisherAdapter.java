package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.event.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuditEventPublisherAdapter implements AuditEventPort {

    private final ApplicationEventPublisher eventPublisher;

    public AuditEventPublisherAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(AuditEvent event) {
        eventPublisher.publishEvent(event);
    }
}
