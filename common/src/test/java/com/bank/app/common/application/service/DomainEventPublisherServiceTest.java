package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPublisherServiceTest {

    private StubEventPublisherPort eventPublisher;
    private DomainEventPublisherService service;

    @BeforeEach
    void setUp() {
        eventPublisher = new StubEventPublisherPort();
        service = new DomainEventPublisherService(eventPublisher);
    }

    @Test
    void publishEventsShouldPublishAllEvents() {
        StubDomainEventProvider provider = new StubDomainEventProvider(
                new TestEvent("event1"),
                new TestEvent("event2")
        );

        service.publishEvents(provider);

        assertThat(eventPublisher.published).hasSize(2);
        assertThat(eventPublisher.published.get(0).toString()).contains("event1");
        assertThat(eventPublisher.published.get(1).toString()).contains("event2");
    }

    @Test
    void publishEventsShouldClearProviderAfterPublishing() {
        StubDomainEventProvider provider = new StubDomainEventProvider(
                new TestEvent("event1")
        );

        service.publishEvents(provider);

        assertThat(provider.cleared).isTrue();
    }

    @Test
    void publishEventsShouldHandleEmptyEvents() {
        StubDomainEventProvider provider = new StubDomainEventProvider();

        service.publishEvents(provider);

        assertThat(eventPublisher.published).isEmpty();
        assertThat(provider.cleared).isTrue();
    }

    private record TestEvent(String data, LocalDateTime occurredAt) implements DomainEvent {
        TestEvent(String data) {
            this(data, LocalDateTime.now());
        }

        @Override
        public String aggregateType() {
            return "Test";
        }

        @Override
        public String aggregateId() {
            return data;
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
