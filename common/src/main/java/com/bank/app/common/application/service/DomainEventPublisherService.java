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

    /**
     * Publishes every pending event and only then clears the provider.
     *
     * <p>Ordering matters on failure: clearing first would drop events that
     * were never delivered. With clear-after-publish, a failed run leaves the
     * events in place so a retry republishes them. If a publish fails after
     * earlier events of the same batch were already delivered, that retry
     * republishes those earlier events too (at-least-once delivery; consumers
     * must tolerate duplicates).
     */
    public void publishEvents(DomainEventProvider provider) {
        List<DomainEvent> events = List.copyOf(provider.getDomainEvents());
        for (DomainEvent event : events) {
            publish(Objects.requireNonNull(event, "Domain event must not be null"));
        }
        provider.clearDomainEvents();
    }

    public void publish(DomainEvent event) {
        eventPublisherPort.publish(event);
    }
}
