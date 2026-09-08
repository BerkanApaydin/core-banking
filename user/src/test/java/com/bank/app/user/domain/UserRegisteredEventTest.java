package com.bank.app.user.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("null")
class UserRegisteredEventTest {

    @Test
    void shouldCreateEventWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        UserRegisteredEvent event = new UserRegisteredEvent("1", "testuser", "ROLE_USER", now);

        assertEquals("1", event.userId());
        assertEquals("testuser", event.username());
        assertEquals("ROLE_USER", event.role());
        assertEquals(now, event.occurredAt());
    }

    @Test
    void shouldFallBackToUnknownWhenUserIdIsNull() {
        UserRegisteredEvent event = new UserRegisteredEvent(null, "testuser", "ROLE_USER", LocalDateTime.now());

        assertEquals("unknown", event.aggregateId());
    }

    @Test
    void shouldExposeUserAggregateIdentity() {
        UserRegisteredEvent event = new UserRegisteredEvent("42", "testuser", "ROLE_USER", LocalDateTime.now());

        assertEquals("User", event.aggregateType());
        assertEquals("42", event.aggregateId());
    }
}
