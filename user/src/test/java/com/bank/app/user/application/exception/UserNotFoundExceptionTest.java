package com.bank.app.user.application.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class UserNotFoundExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        UserNotFoundException ex = new UserNotFoundException("User not found: testuser");
        assertEquals("User not found: testuser", ex.getMessage());
    }

    @Test
    void shouldBeRuntimeException() {
        UserNotFoundException ex = new UserNotFoundException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
