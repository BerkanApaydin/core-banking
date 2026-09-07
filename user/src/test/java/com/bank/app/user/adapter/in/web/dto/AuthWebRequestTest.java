package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWebRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptValidRequest() {
        var request = new AuthWebRequest("alice", "Secret123", "alice@example.com", "+905551112233");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectBlankUsernameAndPassword() {
        var request = new AuthWebRequest("  ", "  ");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectPasswordOverBcryptLimit() {
        // BCrypt silently truncates beyond 72 bytes: longer passwords must be
        // rejected up front, both as a DoS guard and to avoid two different
        // passwords hashing to the same value.
        var request = new AuthWebRequest("alice", "A1" + "x".repeat(71));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectOversizedUsernameAndEmail() {
        var request = new AuthWebRequest("u".repeat(256), "Secret123", "a".repeat(250) + "@ex.com", null);

        assertFalse(validator.validate(request).isEmpty());
    }
}
