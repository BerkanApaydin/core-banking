package com.bank.app.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class OutboxJpaEntityTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);

    @Test
    void shouldCreateEntityWithFullConstructor() {
        OutboxJpaEntity entity = new OutboxJpaEntity(
                "id-1", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", now, true, now.plusHours(1), 3, true, "error", 2);

        assertEquals("id-1", entity.getId());
        assertEquals("transfer", entity.getAggregateType());
        assertEquals("agg-1", entity.getAggregateId());
        assertEquals("TransferCompletedEvent", entity.getEventType());
        assertEquals("{}", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertTrue(entity.isProcessed());
        assertEquals(now.plusHours(1), entity.getProcessedAt());
        assertEquals(3, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
        assertEquals("error", entity.getLastError());
        assertEquals(2, entity.getPartition());
    }

    @Test
    void shouldCreateEntityWithShortConstructor() {
        OutboxJpaEntity entity = new OutboxJpaEntity(
                "id-2", "account", "agg-2", "AccountCreatedEvent",
                "{\"key\":\"value\"}", now, false, 0, false, null, 0);

        assertEquals("id-2", entity.getId());
        assertEquals("account", entity.getAggregateType());
        assertEquals("agg-2", entity.getAggregateId());
        assertEquals("AccountCreatedEvent", entity.getEventType());
        assertEquals("{\"key\":\"value\"}", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertFalse(entity.isProcessed());
        assertNull(entity.getProcessedAt());
        assertEquals(0, entity.getRetryCount());
        assertFalse(entity.isDeadLetter());
        assertNull(entity.getLastError());
        assertEquals(0, entity.getPartition());
    }

    @Test
    void shouldCreateEntityWithNoArgsConstructor() {
        OutboxJpaEntity entity = new OutboxJpaEntity();
        assertNotNull(entity);
    }

    @Test
    void shouldSetAndGetAllFields() {
        OutboxJpaEntity entity = new OutboxJpaEntity();

        entity.setId("id-3");
        entity.setAggregateType("type");
        entity.setAggregateId("agg-3");
        entity.setEventType("EventType");
        entity.setPayload("data");
        entity.setCreatedAt(now);
        entity.setProcessed(true);
        entity.setProcessedAt(now.plusHours(2));
        entity.setRetryCount(5);
        entity.setDeadLetter(true);
        entity.setLastError("fail");
        entity.setPartition(1);

        assertEquals("id-3", entity.getId());
        assertEquals("type", entity.getAggregateType());
        assertEquals("agg-3", entity.getAggregateId());
        assertEquals("EventType", entity.getEventType());
        assertEquals("data", entity.getPayload());
        assertEquals(now, entity.getCreatedAt());
        assertTrue(entity.isProcessed());
        assertEquals(now.plusHours(2), entity.getProcessedAt());
        assertEquals(5, entity.getRetryCount());
        assertTrue(entity.isDeadLetter());
        assertEquals("fail", entity.getLastError());
        assertEquals(1, entity.getPartition());
    }
}
