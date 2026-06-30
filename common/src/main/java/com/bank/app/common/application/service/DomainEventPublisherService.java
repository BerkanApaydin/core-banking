package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;

import java.util.List;
import java.util.Objects;

public class DomainEventPublisherService {

    private final EventPublisherPort eventPublisherPort;

    public DomainEventPublisherService(EventPublisherPort eventPublisherPort) {
        this.eventPublisherPort = eventPublisherPort;
    }

    public void publishEvents(DomainEventProvider provider) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        provider.clearDomainEvents();
        for (DomainEvent event : events) {
            eventPublisherPort.publish(Objects.requireNonNull(event, "Domain event must not be null"));
        }
    }
}
