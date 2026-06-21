package com.bank.app.common.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuditableJpaEntityTest {

    static class TestAuditable extends AuditableJpaEntity {}

    @Test
    void shouldCreateAuditableJpaEntity() {
        TestAuditable entity = new TestAuditable();
        LocalDateTime now = LocalDateTime.now();

        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy("creator");
        entity.setUpdatedBy("updater");

        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
        assertEquals("creator", entity.getCreatedBy());
        assertEquals("updater", entity.getUpdatedBy());
    }

    @Test
    void shouldCreateIdempotencyKeyJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity("key", "PENDING", "body", now);

        assertEquals("key", entity.getKey());
        assertEquals("PENDING", entity.getStatus());
        assertEquals("body", entity.getResponseBody());
        assertEquals(now, entity.getCreatedAt());

        IdempotencyKeyJpaEntity empty = new IdempotencyKeyJpaEntity();
        empty.setKey("key2");
        empty.setStatus("COMPLETED");
        empty.setResponseBody("body2");
        empty.setCreatedAt(now);

        assertEquals("key2", empty.getKey());
        assertEquals("COMPLETED", empty.getStatus());
        assertEquals("body2", empty.getResponseBody());
        assertEquals(now, empty.getCreatedAt());
    }
}
