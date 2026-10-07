package com.bank.app.user.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AuthResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        AuthResponse response = new AuthResponse("jwt.token.here", "refresh.token.here", 1L, "testuser", 900000L);

        assertEquals("jwt.token.here", response.token());
        assertEquals("refresh.token.here", response.refreshToken());
        assertEquals(1L, response.userId());
        assertEquals("testuser", response.username());
        assertEquals(900000L, response.expiresInMs());
    }

    @Test
    void shouldHandleNullToken() {
        AuthResponse response = new AuthResponse(null, null, 1L, "testuser", 900000L);

        assertNull(response.token());
        assertNull(response.refreshToken());
        assertEquals(1L, response.userId());
        assertEquals("testuser", response.username());
    }

    @Test
    void shouldHandleNullUsername() {
        AuthResponse response = new AuthResponse("token", "refresh", 1L, null, 900000L);

        assertEquals("token", response.token());
        assertEquals(1L, response.userId());
        assertNull(response.username());
    }
}
