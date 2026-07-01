package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPublisherTest {

    @Test
    void publishEventsShouldPublishAllEvents() {
        StubEventPublisherPort port = new StubEventPublisherPort();
        StubDomainEventProvider provider = new StubDomainEventProvider(
                new TestEvent("event1"),
                new TestEvent("event2")
        );

        DomainEventPublisher.publishEvents(provider, port);

        assertThat(port.published).hasSize(2);
    }

    @Test
    void publishEventsShouldClearProvider() {
        StubEventPublisherPort port = new StubEventPublisherPort();
        StubDomainEventProvider provider = new StubDomainEventProvider(
                new TestEvent("event1")
        );

        DomainEventPublisher.publishEvents(provider, port);

        assertThat(provider.cleared).isTrue();
    }

    private record TestEvent(String data, LocalDateTime occurredAt) implements DomainEvent {
        TestEvent(String data) {
            this(data, LocalDateTime.now());
        }
    }

    private static class StubDomainEventProvider implements DomainEventProvider {
        private final List<DomainEvent> events;
        boolean cleared;

        StubDomainEventProvider(DomainEvent... events) {
            this.events = new ArrayList<>(List.of(events));
        }

        @Override
        public List<DomainEvent> getDomainEvents() {
            return events;
        }

        @Override
        public void clearDomainEvents() {
            cleared = true;
        }
    }

    private static class StubEventPublisherPort implements EventPublisherPort {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
