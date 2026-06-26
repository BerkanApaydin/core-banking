package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthWebRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void shouldCreateWithUsernameAndPassword() {
        AuthWebRequest request = new AuthWebRequest("user1", "pass123");
        assertEquals("user1", request.username());
        assertEquals("pass123", request.password());
        assertNull(request.email());
        assertNull(request.phone());
    }

    @Test
    void shouldCreateWithAllFields() {
        AuthWebRequest request = new AuthWebRequest("user1", "pass123", "test@example.com", "5551234567");
        assertEquals("user1", request.username());
        assertEquals("pass123", request.password());
        assertEquals("test@example.com", request.email());
        assertEquals("5551234567", request.phone());
    }

    @Test
    void shouldFailValidationWhenUsernameBlank() {
        AuthWebRequest request = new AuthWebRequest("", "pass123");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenPasswordBlank() {
        AuthWebRequest request = new AuthWebRequest("user1", "");
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldFailValidationWhenEmailInvalid() {
        AuthWebRequest request = new AuthWebRequest("user1", "pass123", "invalid-email", null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
