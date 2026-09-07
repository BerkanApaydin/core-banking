package com.bank.app.persistence;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AuditableJpaEntityTest {

    static class TestAuditable extends AuditableJpaEntity {
    }

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
}
