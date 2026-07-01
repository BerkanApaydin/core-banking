package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.domain.event.AuditEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringEventPublisherAdapter implements AuditEventPort {

    private final ApplicationEventPublisher eventPublisher;

    public SpringEventPublisherAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(AuditEvent event) {
        eventPublisher.publishEvent(event);
    }
}
