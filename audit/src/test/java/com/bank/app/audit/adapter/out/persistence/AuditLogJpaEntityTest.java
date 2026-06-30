package com.bank.app.audit.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditLogJpaEntityTest {

    @Test
    void shouldCreateAuditLogJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(1L, "user", "ACCOUNT_CREATED", "details", now);

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals("ACCOUNT_CREATED", entity.getAction());
        assertEquals("details", entity.getDetails());
        assertEquals(now, entity.getTimestamp());

        AuditLogJpaEntity empty = new AuditLogJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setAction("TRANSFER_EXECUTED");
        empty.setDetails("details2");
        empty.setTimestamp(now);

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals("TRANSFER_EXECUTED", empty.getAction());
        assertEquals("details2", empty.getDetails());
        assertEquals(now, empty.getTimestamp());
    }
}
