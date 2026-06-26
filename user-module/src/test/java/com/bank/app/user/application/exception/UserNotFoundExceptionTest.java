package com.bank.app.user.application.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class UserNotFoundExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        UserNotFoundException ex = new UserNotFoundException("Kullanıcı bulunamadı: testuser");
        assertEquals("Kullanıcı bulunamadı: testuser", ex.getMessage());
    }

    @Test
    void shouldBeRuntimeException() {
        UserNotFoundException ex = new UserNotFoundException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
