package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The refresh endpoint is public, so the body cap is a memory-DoS guard: one
 * giant token string must be rejected before it is hashed or persisted.
 */
@SuppressWarnings("null")
@DisplayName("RefreshTokenWebRequest")
class RefreshTokenWebRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messageTemplatesFor(RefreshTokenWebRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("should accept a normal refresh token")
    void shouldAcceptANormalToken() {
        var request = new RefreshTokenWebRequest("eyJhbGciOiJIUzI1NiJ9.payload.signature");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.refreshToken()).isEqualTo("eyJhbGciOiJIUzI1NiJ9.payload.signature");
    }

    @Test
    @DisplayName("should accept exactly 4096 characters")
    void shouldAcceptExactlyTheCap() {
        var request = new RefreshTokenWebRequest("a".repeat(4096));

        assertThat(request.refreshToken()).hasSize(4096);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("should reject 4097 characters")
    void shouldRejectOneCharacterOverTheCap() {
        var request = new RefreshTokenWebRequest("a".repeat(4097));

        assertThat(messageTemplatesFor(request)).containsExactly("{validation.refreshToken.tooLong}");
    }

    @Test
    @DisplayName("should reject a blank token")
    void shouldRejectABlankToken() {
        var request = new RefreshTokenWebRequest("   ");

        assertThat(messageTemplatesFor(request)).containsExactly("{validation.refreshToken.required}");
    }

    @Test
    @DisplayName("should reject a null token")
    void shouldRejectANullToken() {
        var request = new RefreshTokenWebRequest(null);

        assertThat(messageTemplatesFor(request)).containsExactly("{validation.refreshToken.required}");
    }
}
