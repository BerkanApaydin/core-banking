package com.bank.app.user.domain.exception;

import com.bank.app.common.domain.exception.BusinessFailureKind;
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

    @Test
    void shouldDescribeMissingUser() {
        UserNotFoundException ex = new UserNotFoundException("test");
        assertEquals(BusinessFailureKind.NOT_FOUND, ex.getFailureKind());
    }
}
