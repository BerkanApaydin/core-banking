package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterWebRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptValidRequest() {
        var request = new RegisterWebRequest("alice", "Secret123", "alice@example.com", "+905551112233");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAcceptNullOptionals() {
        var request = new RegisterWebRequest("alice", "Secret123", null, null);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectShortPassword() {
        // Policy minimum is 8: short secrets must fail at the web boundary,
        // not deep inside the use case.
        var request = new RegisterWebRequest("alice", "Short1", null, null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectInvalidPhone() {
        var request = new RegisterWebRequest("alice", "Secret123", null, "not-a-phone");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectBlankUsernameAndPassword() {
        var request = new RegisterWebRequest("  ", "  ", null, null);

        assertFalse(validator.validate(request).isEmpty());
    }
}
