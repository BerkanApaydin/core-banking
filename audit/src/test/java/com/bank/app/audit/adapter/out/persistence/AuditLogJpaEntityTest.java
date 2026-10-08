package com.bank.app.audit.adapter.out.persistence;

import com.bank.app.audit.domain.AuditAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditLogJpaEntityTest {

    @Test
    void shouldCreateAuditLogJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        AuditLogJpaEntity entity = new AuditLogJpaEntity(1L, "user", AuditAction.ACCOUNT_CREATED, "details", now);

        assertEquals(1L, entity.getId());
        assertEquals("user", entity.getUsername());
        assertEquals(AuditAction.ACCOUNT_CREATED, entity.getAction());
        assertEquals("details", entity.getDetails());
        assertEquals(now, entity.getTimestamp());

        AuditLogJpaEntity empty = new AuditLogJpaEntity();
        empty.setId(2L);
        empty.setUsername("user2");
        empty.setAction(AuditAction.TRANSFER_EXECUTED);
        empty.setDetails("details2");
        empty.setTimestamp(now);

        assertEquals(2L, empty.getId());
        assertEquals("user2", empty.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, empty.getAction());
        assertEquals("details2", empty.getDetails());
        assertEquals(now, empty.getTimestamp());
    }

    @Test
    void shouldExposeActorUserId() {
        // Kills the PrimitiveReturns mutant (0L vs actual id): a non-zero id
        // must round-trip, and null must stay null (not 0).
        LocalDateTime now = LocalDateTime.now();
        AuditLogJpaEntity withActor = new AuditLogJpaEntity(1L, "user",
                AuditAction.ACCOUNT_CREATED, "details", now, 42L);
        assertEquals(42L, withActor.getActorUserId());

        AuditLogJpaEntity withoutActor = new AuditLogJpaEntity(2L, "user",
                AuditAction.ACCOUNT_CREATED, "details", now);
        assertNull(withoutActor.getActorUserId());

        withoutActor.setActorUserId(7L);
        assertEquals(7L, withoutActor.getActorUserId());
    }
}
