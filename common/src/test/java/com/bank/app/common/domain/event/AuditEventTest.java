package com.bank.app.common.domain.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuditEventTest {

    @Test
    void shouldCreateWithValidValues() {
        LocalDateTime now = LocalDateTime.now();
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "Details", now);
        assertEquals("ACCOUNT_CREATED", event.action());
        assertEquals("Details", event.details());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldAcceptNullAction() {
        AuditEvent event = new AuditEvent(null, "Details", LocalDateTime.now());
        assertNull(event.action());
    }

    @Test
    void shouldAcceptNullDetails() {
        AuditEvent event = new AuditEvent("ACTION", null, LocalDateTime.now());
        assertNull(event.details());
    }

    @Test
    void shouldAcceptNullOccurredAt() {
        AuditEvent event = new AuditEvent("ACTION", "Details", null);
        assertNull(event.occurredAt());
    }

    @Test
    void shouldBeEqualWhenSameValues() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(new AuditEvent("A", "B", now), new AuditEvent("A", "B", now));
    }

    @Test
    void shouldNotBeEqualWhenDifferentAction() {
        LocalDateTime now = LocalDateTime.now();
        assertNotEquals(new AuditEvent("A", "B", now), new AuditEvent("C", "B", now));
    }

    @Test
    void shouldHaveSameHashCodeWhenSameValues() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(
                new AuditEvent("A", "B", now).hashCode(),
                new AuditEvent("A", "B", now).hashCode());
    }

    @Test
    void shouldImplementDomainEvent() {
        AuditEvent event = new AuditEvent("A", "B", LocalDateTime.now());
        assertInstanceOf(DomainEvent.class, event);
    }

    @Test
    void shouldReturnStringRepresentation() {
        AuditEvent event = new AuditEvent("ACCOUNT_CREATED", "Details", LocalDateTime.of(2026, 6, 25, 12, 0));
        String str = event.toString();
        assertTrue(str.contains("ACCOUNT_CREATED"));
        assertTrue(str.contains("Details"));
    }

    @Test
    void shouldDefaultToSystemUserWhenUsernameIsNull() {
        AuditEvent event = new AuditEvent("ACTION", "Details", LocalDateTime.now(), null);
        assertEquals("system", event.username());
    }

    @Test
    void shouldDefaultToSystemUserWhenUsernameIsBlank() {
        AuditEvent event = new AuditEvent("ACTION", "Details", LocalDateTime.now(), "   ");
        assertEquals("system", event.username());
    }

    @Test
    void shouldKeepExplicitUsername() {
        AuditEvent event = new AuditEvent("ACTION", "Details", LocalDateTime.now(), "alice");
        assertEquals("alice", event.username());
    }

    @Test
    void shouldExposeAggregateIdentity() {
        AuditEvent event = new AuditEvent("ACTION", "Details", LocalDateTime.now());
        assertEquals("Audit", event.aggregateType());
        assertEquals("system", event.aggregateId());
    }
}
