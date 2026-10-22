package com.bank.app.common.application.service;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.event.DomainEventProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void publishEventsShouldNotClearProviderWhenPublishFails() {
        StubDomainEventProvider provider = new StubDomainEventProvider(
                new TestEvent("event1"),
                new TestEvent("event2")
        );
        eventPublisher.failOnPublishNumber = 2;

        assertThatThrownBy(() -> service.publishEvents(provider))
                .isInstanceOf(IllegalStateException.class);

        assertThat(eventPublisher.published).hasSize(1);
        assertThat(provider.cleared).isFalse();
    }

    @Test
    void publishEventsShouldHandleEmptyEvents() {
        StubDomainEventProvider provider = new StubDomainEventProvider();

        service.publishEvents(provider);

        assertThat(eventPublisher.published).isEmpty();
        assertThat(provider.cleared).isTrue();
    }

    @Test
    void publishEventsShouldNeverCallThePublisherForAnEmptyEventList() {
        StubDomainEventProvider provider = new StubDomainEventProvider();

        service.publishEvents(provider);

        // The port must not be called at all - not even with a null event.
        assertThat(eventPublisher.publishCalls).isZero();
        assertThat(provider.cleared).isTrue();
    }

    @Test
    void publishEventsShouldRejectANullProvider() {
        assertThatThrownBy(() -> service.publishEvents(null))
                .isInstanceOf(NullPointerException.class);

        assertThat(eventPublisher.publishCalls).isZero();
    }

    @Test
    void publishEventsShouldRejectANullEventElement() {
        ListDomainEventProvider provider = new ListDomainEventProvider(
                Arrays.asList(new TestEvent("event1"), null));

        assertThatThrownBy(() -> service.publishEvents(provider))
                .isInstanceOf(NullPointerException.class);

        // A null element must not be published and must not clear the batch:
        // clearing would drop the remaining events of the batch for good.
        assertThat(eventPublisher.publishCalls).isZero();
        assertThat(provider.cleared).isFalse();
    }

    @Test
    void publishEventsShouldRejectANullEventList() {
        ListDomainEventProvider provider = new ListDomainEventProvider(null);

        assertThatThrownBy(() -> service.publishEvents(provider))
                .isInstanceOf(NullPointerException.class);

        assertThat(eventPublisher.publishCalls).isZero();
        assertThat(provider.cleared).isFalse();
    }

    @Test
    void publishShouldForwardTheEventToThePort() {
        TestEvent event = new TestEvent("event1");

        service.publish(event);

        assertThat(eventPublisher.publishCalls).isEqualTo(1);
        assertThat(eventPublisher.published).containsExactly(event);
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
        int failOnPublishNumber = -1;
        int publishCalls;

        @Override
        public void publish(DomainEvent event) {
            if (++publishCalls == failOnPublishNumber) {
                throw new IllegalStateException("publish failed");
            }
            published.add(event);
        }
    }

    /** Provider that can hand out a batch that contains a null element. */
    private static class ListDomainEventProvider implements DomainEventProvider {
        private final List<DomainEvent> events;
        boolean cleared;

        ListDomainEventProvider(List<DomainEvent> events) {
            this.events = events;
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
}
