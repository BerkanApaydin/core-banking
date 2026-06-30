package com.bank.app.infrastructure.adapter.out.event;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;

    public SpringEventPublisherAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(@NonNull DomainEvent event) {
        eventPublisher.publishEvent(event);
    }
}
