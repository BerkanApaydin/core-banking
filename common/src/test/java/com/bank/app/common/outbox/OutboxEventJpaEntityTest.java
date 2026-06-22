package com.bank.app.common.outbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventJpaEntityTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);

    @Test
    void shouldCreateEntityWithConstructor() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id-1", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", now, false, null, 0, false, null, 0);

        assertEquals("id-1", entity.getId());
        assertEquals("transfer", entity.getAggregateType());
        assertEquals("agg-1", entity.getAggregateId());
        assertEquals("TransferCompletedEvent", entity.getEventType());
        assertEquals("{}", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertFalse(entity.isProcessed());
        assertNull(entity.getProcessedAt());
        assertEquals(0, entity.getRetryCount());
        assertFalse(entity.isDeadLetter());
        assertNull(entity.getLastError());
        assertEquals(0, entity.getPartition());
    }

    @Test
    void shouldCreateEntityWithShortConstructor() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id-2", "account", "agg-2", "AccountCreatedEvent",
                "{\"key\":\"value\"}", now, true, now);

        assertEquals("id-2", entity.getId());
        assertTrue(entity.isProcessed());
        assertEquals(now, entity.getProcessedAt());
        assertEquals(0, entity.getRetryCount());
        assertFalse(entity.isDeadLetter());
    }

    @Test
    void shouldCreateEntityWithMediumConstructor() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity(
                "id-3", "user", "agg-3", "UserCreatedEvent",
                "{}", now, true, now, 3, true, "error");

        assertTrue(entity.isDeadLetter());
        assertEquals(3, entity.getRetryCount());
        assertEquals("error", entity.getLastError());
        assertEquals(0, entity.getPartition());
    }

    @Test
    void shouldSetAndGetAllFields() {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        LocalDateTime later = now.plusHours(1);

        entity.setId("id-4");
        entity.setAggregateType("type");
        entity.setAggregateId("agg-4");
        entity.setEventType("EventType");
        entity.setPayload("data");
        entity.setCreatedAt(now);
        entity.setProcessed(true);
        entity.setProcessedAt(later);
        entity.setRetryCount(5);
        entity.setDeadLetter(true);
        entity.setLastError("fail");
        entity.setPartition(2);

        assertEquals("id-4", entity.getId());
        assertEquals("type", entity.getAggregateType());
        assertEquals("agg-4", entity.getAggregateId());
        assertEquals("EventType", entity.getEventType());
        assertEquals("data", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertTrue(entity.isProcessed());
        assertEquals(later, entity.getProcessedAt());
        assertEquals(5, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
        assertEquals("fail", entity.getLastError());
        assertEquals(2, entity.getPartition());
    }
}
