package com.bank.app.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class IdempotencyKeyJpaEntityTest {

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

    @Test
    void shouldExposeAssignedIdViaPersistableContract() {
        IdempotencyKeyJpaEntity entity =
                new IdempotencyKeyJpaEntity("persist-key", "PENDING", "body", LocalDateTime.now());

        assertEquals("persist-key", entity.getId());
        assertEquals(entity.getKey(), entity.getId());
    }

    @Test
    void shouldTrackNewStateUntilPostLoadOrPrePersist() {
        IdempotencyKeyJpaEntity entity =
                new IdempotencyKeyJpaEntity("new-key", "PENDING", "body", LocalDateTime.now());

        assertTrue(entity.isNew());

        entity.markNotNew();

        assertFalse(entity.isNew());
    }

    @Test
    void shouldRoundTripRequestHash() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();
        assertNull(entity.getRequestHash());

        entity.setRequestHash("abc123hash");

        assertEquals("abc123hash", entity.getRequestHash());
    }

    @Test
    void shouldDefaultKeyKindToHttpAndRoundTrip() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();

        assertEquals("HTTP", entity.getKeyKind());

        entity.setKeyKind("HANDLER");

        assertEquals("HANDLER", entity.getKeyKind());
    }

    @Test
    void shouldRoundTripResponseStatus() {
        IdempotencyKeyJpaEntity withStatus = new IdempotencyKeyJpaEntity(
                "key", "COMPLETED", "body", 200, LocalDateTime.now());

        assertEquals(200, withStatus.getResponseStatus());

        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();
        assertNull(entity.getResponseStatus());
        entity.setResponseStatus(409);
        assertEquals(409, entity.getResponseStatus());
    }
}
