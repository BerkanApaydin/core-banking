package com.bank.app.common.domain;

import com.bank.app.common.domain.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("BaseAggregateRoot")
class BaseAggregateRootTest {

    private static class TestAggregate extends BaseAggregateRoot {
        void triggerEvent(DomainEvent event) {
            registerEvent(event);
        }
    }

    private record TestEvent(String data, LocalDateTime occurredAt) implements DomainEvent {}

    private final TestAggregate aggregate = new TestAggregate();

    @Nested
    @DisplayName("domain event management")
    class DomainEventManagement {

        @Test
        @DisplayName("should start with no domain events")
        void shouldStartWithNoEvents() {
            assertThat(aggregate.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("should register a domain event")
        void shouldRegisterEvent() {
            TestEvent event = new TestEvent("test", LocalDateTime.now());

            aggregate.triggerEvent(event);

            assertThat(aggregate.getDomainEvents()).hasSize(1);
            assertThat(aggregate.getDomainEvents().getFirst()).isSameAs(event);
        }

        @Test
        @DisplayName("should register multiple events in order")
        void shouldRegisterMultipleEvents() {
            TestEvent event1 = new TestEvent("first", LocalDateTime.now());
            TestEvent event2 = new TestEvent("second", LocalDateTime.now());

            aggregate.triggerEvent(event1);
            aggregate.triggerEvent(event2);

            assertThat(aggregate.getDomainEvents()).hasSize(2);
            assertThat(aggregate.getDomainEvents().get(0).toString()).contains("first");
            assertThat(aggregate.getDomainEvents().get(1).toString()).contains("second");
        }

        @Test
        @DisplayName("should clear all domain events")
        void shouldClearEvents() {
            aggregate.triggerEvent(new TestEvent("test", LocalDateTime.now()));
            aggregate.triggerEvent(new TestEvent("test2", LocalDateTime.now()));

            aggregate.clearDomainEvents();

            assertThat(aggregate.getDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            aggregate.triggerEvent(new TestEvent("test", LocalDateTime.now()));
            var events = aggregate.getDomainEvents();

            assertThatThrownBy(events::clear)
                    .isExactlyInstanceOf(UnsupportedOperationException.class);
        }
    }
}
