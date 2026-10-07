package com.bank.app.user.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuthRequestTest {

    @Test
    void shouldCreateWithAllFields() {
        AuthRequest request = new AuthRequest("testuser", "Password1!", "test@example.com", "5551234567");

        assertEquals("testuser", request.username());
        assertEquals("Password1!", request.password());
        assertEquals("test@example.com", request.email());
        assertEquals("5551234567", request.phone());
    }

    @Test
    void shouldCreateWithCompactConstructor() {
        AuthRequest request = new AuthRequest("testuser", "Password1!");

        assertEquals("testuser", request.username());
        assertEquals("Password1!", request.password());
        assertNull(request.email());
        assertNull(request.phone());
    }

    @Test
    void shouldHandleNullEmailAndPhone() {
        AuthRequest request = new AuthRequest("user", "pass", null, null);

        assertEquals("user", request.username());
        assertEquals("pass", request.password());
        assertNull(request.email());
        assertNull(request.phone());
    }

    @Test
    void shouldRejectBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> new AuthRequest("", "Password1!"));
        assertThrows(IllegalArgumentException.class, () -> new AuthRequest("   ", "Password1!"));
    }

    @Test
    void shouldRejectNullUsernameAndPassword() {
        // Application-boundary guarantee: the login use-case can never see
        // a null username, so its guard reset cannot hit the NPE path (L5).
        assertThrows(NullPointerException.class, () -> new AuthRequest(null, "Password1!"));
        assertThrows(NullPointerException.class, () -> new AuthRequest("user", null));
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> new AuthRequest("user", ""));
        assertThrows(IllegalArgumentException.class, () -> new AuthRequest("user", "   "));
    }
}
