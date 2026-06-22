package com.bank.app.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationExceptionTest {

    @Test
    void shouldCreateWithMessageOnly() {
        AuthorizationException ex = new AuthorizationException("Yetki yok");
        assertEquals("Yetki yok", ex.getMessage());
        assertNull(ex.getMessageKey());
        assertNull(ex.getArgs());
    }

    @Test
    void shouldCreateWithMessageKeyAndArgs() {
        AuthorizationException ex = new AuthorizationException("error.auth", new Object[]{"admin"}, "Yetki yok: admin");
        assertEquals("Yetki yok: admin", ex.getMessage());
        assertEquals("error.auth", ex.getMessageKey());
        assertArrayEquals(new Object[]{"admin"}, ex.getArgs());
    }

    @Test
    void shouldReturnMessageKeyWhenPresent() {
        AuthorizationException ex = new AuthorizationException("error.access_denied", new Object[]{}, "Erişim reddedildi");
        assertEquals("error.access_denied", ex.getMessageKey());
    }

    @Test
    void shouldReturnNullMessageKeyWhenUsingSimpleConstructor() {
        AuthorizationException ex = new AuthorizationException("Yetki yok");
        assertNull(ex.getMessageKey());
    }

    @Test
    void shouldBeRuntimeException() {
        AuthorizationException ex = new AuthorizationException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
