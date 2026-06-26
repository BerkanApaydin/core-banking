package com.bank.app.user.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuthResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        AuthResponse response = new AuthResponse("jwt.token.here", 1L, "testuser");

        assertEquals("jwt.token.here", response.token());
        assertEquals(1L, response.userId());
        assertEquals("testuser", response.username());
    }

    @Test
    void shouldHandleNullToken() {
        AuthResponse response = new AuthResponse(null, 1L, "testuser");

        assertNull(response.token());
        assertEquals(1L, response.userId());
        assertEquals("testuser", response.username());
    }

    @Test
    void shouldHandleNullUsername() {
        AuthResponse response = new AuthResponse("token", 1L, null);

        assertEquals("token", response.token());
        assertEquals(1L, response.userId());
        assertNull(response.username());
    }
}
