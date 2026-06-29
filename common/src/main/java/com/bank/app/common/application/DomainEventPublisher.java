package com.bank.app.common.application;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;

import java.util.List;
import java.util.Objects;

public final class DomainEventPublisher {

    private DomainEventPublisher() {}

    public static void publishEvents(DomainEventProvider provider, EventPublisherPort port) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        provider.clearDomainEvents();
        for (DomainEvent event : events) {
            port.publish(Objects.requireNonNull(event, "Domain event must not be null"));
        }
    }
}
