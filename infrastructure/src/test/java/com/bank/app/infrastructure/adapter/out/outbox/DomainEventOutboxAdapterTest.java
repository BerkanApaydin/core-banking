package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.event.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DomainEventOutboxAdapterTest {

    private static final String AGGREGATE_TYPE = "TestAggregate";
    private static final String AGGREGATE_ID = "agg-123";

    @Nested
    class WithMockObjectMapper {

        @Mock
        private OutboxPort outboxPort;

        @Mock
        private ObjectMapper objectMapper;

        @Captor
        private ArgumentCaptor<OutboxPort.EventEntry> entryCaptor;

        private DomainEventOutboxAdapter adapter;

        private static final String TEST_PAYLOAD = "{\"key\":\"value\"}";

        @BeforeEach
        void setUp() throws Exception {
            adapter = new DomainEventOutboxAdapter(outboxPort, objectMapper);
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn(TEST_PAYLOAD);
        }

        private DomainEvent createEvent(String aggregateId) {
            return new DomainEvent() {
                @Override
                public LocalDateTime occurredAt() {
                    return LocalDateTime.now();
                }

                @Override
                public String aggregateType() {
                    return AGGREGATE_TYPE;
                }

                @Override
                public String aggregateId() {
                    return aggregateId;
                }
            };
        }

        @Test
        void shouldSaveSerializedEventToOutbox() throws Exception {
            DomainEvent event = createEvent(AGGREGATE_ID);

            adapter.publish(event);

            verify(outboxPort).save(entryCaptor.capture());
            verify(objectMapper).writeValueAsString(event);

            OutboxPort.EventEntry entry = entryCaptor.getValue();
            assertEquals(TEST_PAYLOAD, entry.payload());
            assertEquals(AGGREGATE_TYPE, entry.aggregateType());
            assertEquals(AGGREGATE_ID, entry.aggregateId());
            assertEquals(event.getClass().getSimpleName(), entry.eventType());
            assertNotNull(entry.id());
            assertEquals(0, entry.retryCount());
            assertFalse(entry.processed());
            assertFalse(entry.deadLetter());
            assertNull(entry.lastError());
            assertNotNull(entry.createdAt());
        }

        @Test
        void shouldResolvePartitionFromAggregateId() {
            DomainEvent event = createEvent(AGGREGATE_ID);

            adapter.publish(event);

            verify(outboxPort).save(entryCaptor.capture());
            int expectedPartition = Math.abs(AGGREGATE_ID.hashCode() % 16);
            assertEquals(expectedPartition, entryCaptor.getValue().partition());
        }

        @Test
        void shouldUsePartitionZeroWhenAggregateIdIsNull() {
            DomainEvent event = createEvent(null);

            adapter.publish(event);

            verify(outboxPort).save(entryCaptor.capture());
            assertEquals(0, entryCaptor.getValue().partition());
        }

        @Test
        void shouldThrowWhenSerializationFails() throws Exception {
            DomainEvent event = createEvent(AGGREGATE_ID);
            when(objectMapper.writeValueAsString(event)).thenThrow(new JsonProcessingException("serialization error") {});

            RuntimeException ex = assertThrows(RuntimeException.class, () -> adapter.publish(event));
            assertTrue(ex.getMessage().contains("Failed to serialize domain event"));
            verify(outboxPort, never()).save(any());
        }

        @Test
        void shouldPublishDifferentEventsWithUniqueIds() {
            DomainEvent event1 = createEvent(AGGREGATE_ID);
            DomainEvent event2 = createEvent(AGGREGATE_ID);

            adapter.publish(event1);
            adapter.publish(event2);

            verify(outboxPort, times(2)).save(entryCaptor.capture());
            String id1 = entryCaptor.getAllValues().get(0).id();
            String id2 = entryCaptor.getAllValues().get(1).id();
            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2);
        }

        @Test
        void shouldSerializeWithObjectMapper() throws Exception {
            ObjectMapper realMapper = new ObjectMapper();
            adapter = new DomainEventOutboxAdapter(outboxPort, realMapper);
            DomainEvent event = new DomainEvent() {
                @Override public LocalDateTime occurredAt() { return LocalDateTime.of(2024, 1, 15, 10, 30); }
                @Override public String aggregateType() { return "TestAggregate"; }
                @Override public String aggregateId() { return "agg-123"; }
                public String getType() { return "test-event"; }
            };

            adapter.publish(event);

            verify(outboxPort).save(entryCaptor.capture());
            assertNotNull(entryCaptor.getValue().payload());
            assertTrue(entryCaptor.getValue().payload().contains("test-event"));
        }
    }
}
