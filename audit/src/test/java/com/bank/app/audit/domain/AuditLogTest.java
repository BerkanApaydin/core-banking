package com.bank.app.audit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
@DisplayName("AuditLog")
class AuditLogTest {

    @Test
    @DisplayName("should create with constructor and set all fields")
    void shouldCreateAuditLogWithConstructor() {
        AuditLog log = new AuditLog(1L, "testuser", AuditAction.ACCOUNT_CREATED, "Account created",
                LocalDateTime.of(2025, 1, 15, 10, 0));

        assertEquals(1L, log.getId());
        assertEquals("testuser", log.getUsername());
        assertEquals(AuditAction.ACCOUNT_CREATED, log.getAction());
        assertEquals("Account created", log.getDetails());
        assertEquals(LocalDateTime.of(2025, 1, 15, 10, 0), log.getTimestamp());
    }

    @Test
    @DisplayName("should create with factory method and auto-generate timestamp")
    void shouldCreateAuditLogWithFactoryMethod() {
        AuditLog log = AuditLog.create("testuser", AuditAction.TRANSFER_CANCELLED, "Transfer 10 cancelled");

        assertNull(log.getId());
        assertEquals("testuser", log.getUsername());
        assertEquals(AuditAction.TRANSFER_CANCELLED, log.getAction());
        assertEquals("Transfer 10 cancelled", log.getDetails());
        assertNotNull(log.getTimestamp());
    }

    @Test
    @DisplayName("should throw when username is null")
    void shouldThrowNullPointerExceptionWhenUsernameIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AuditLog(1L, null, AuditAction.ACCOUNT_CREATED, "details", LocalDateTime.now()));
    }

    @Test
    @DisplayName("should throw when action is null")
    void shouldThrowNullPointerExceptionWhenActionIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AuditLog(1L, "user", null, "details", LocalDateTime.now()));
    }

    @Test
    @DisplayName("should throw when details is null")
    void shouldThrowNullPointerExceptionWhenDetailsIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AuditLog(1L, "user", AuditAction.ACCOUNT_CREATED, null, LocalDateTime.now()));
    }

    @Test
    @DisplayName("should throw when timestamp is null")
    void shouldThrowNullPointerExceptionWhenTimestampIsNull() {
        assertThrows(NullPointerException.class,
                () -> new AuditLog(1L, "user", AuditAction.ACCOUNT_CREATED, "details", null));
    }

    @Test
    @DisplayName("should accept null id for new records")
    void shouldAcceptNullId() {
        AuditLog log = new AuditLog(null, "user", AuditAction.ACCOUNT_CREATED, "details", LocalDateTime.now());
        assertNull(log.getId());
    }

    @Test
    @DisplayName("should create with custom clock")
    void shouldCreateWithClock() {
        LocalDateTime fixedTime = LocalDateTime.of(2026, 6, 24, 12, 30);
        Clock clock = Clock.fixed(fixedTime.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        AuditLog log = AuditLog.create("testuser", AuditAction.ACCOUNT_CREATED, "details", clock);
        assertEquals(fixedTime, log.getTimestamp());
    }
}
