package com.bank.app.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentRequestExceptionTest {

    @Test
    void shouldCreateConcurrentRequestExceptionWithMessage() {
        ConcurrentRequestException ex = new ConcurrentRequestException("concurrent");
        assertEquals("concurrent", ex.getMessage());
    }

    @Test
    void shouldCreateConcurrentRequestExceptionWithKeyAndArgs() {
        ConcurrentRequestException ex = new ConcurrentRequestException("key", new Object[]{"arg"}, "default");
        assertEquals("default", ex.getMessage());
        assertEquals("key", ex.getMessageKey());
        assertArrayEquals(new Object[]{"arg"}, ex.getArgs());
    }
}
