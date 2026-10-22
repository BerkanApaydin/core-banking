package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthWebRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> totpCodeTemplates(AuthWebRequest request) {
        return validator.validate(request).stream()
                .filter(violation -> "totpCode".equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
    }

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

    @Test
    void shouldRejectOversizedPhone() {
        // PhoneNumber VO caps at 20 chars (+ optional '+'); the wire shape
        // must reject longer input up front.
        var request = new AuthWebRequest("alice", "Secret123", "alice@example.com", "+" + "1".repeat(21));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void shouldAcceptSixDigitTotpCode() {
        var request = new AuthWebRequest("alice", "Secret123", null, null, "123456");

        assertTrue(validator.validate(request).isEmpty());
        assertTrue(totpCodeTemplates(request).isEmpty());
    }

    @Test
    void shouldAcceptEightDigitTotpCode() {
        // The 8-digit policy is configurable, so the wire shape must accept
        // both ends of the 6-8 range.
        var request = new AuthWebRequest("alice", "Secret123", null, null, "12345678");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void shouldRejectTotpCodeOutsideTheDigitRange() {
        // A malformed code must 400 at the web layer: otherwise it reaches
        // the use case and is charged to the victim's MFA budget, so an
        // attacker could invalidate someone's second factor with garbage.
        var tooShort = new AuthWebRequest("alice", "Secret123", null, null, "12345");
        var tooLong = new AuthWebRequest("alice", "Secret123", null, null, "123456789");

        assertFalse(validator.validate(tooShort).isEmpty());
        assertFalse(validator.validate(tooLong).isEmpty());
        assertTrue(totpCodeTemplates(tooShort).contains("{validation.totp.invalid}"));
        assertTrue(totpCodeTemplates(tooLong).contains("{validation.totp.invalid}"));
    }

    @Test
    void shouldRejectNonNumericTotpCode() {
        var request = new AuthWebRequest("alice", "Secret123", null, null, "abcdef");

        assertFalse(validator.validate(request).isEmpty());
        assertTrue(totpCodeTemplates(request).contains("{validation.totp.invalid}"));
    }

    @Test
    void shouldRejectPaddedTotpCode() {
        // The pattern anchors the whole value: a padded code must not slip
        // through the edge and be trimmed downstream.
        var request = new AuthWebRequest("alice", "Secret123", null, null, " 123456 ");

        assertFalse(validator.validate(request).isEmpty());
        assertTrue(totpCodeTemplates(request).contains("{validation.totp.invalid}"));
    }

    @Test
    void shouldRejectBlankTotpCode() {
        var request = new AuthWebRequest("alice", "Secret123", null, null, "   ");

        assertFalse(validator.validate(request).isEmpty());
        assertTrue(totpCodeTemplates(request).contains("{validation.totp.invalid}"));
    }

    @Test
    void shouldAcceptAnAbsentTotpCodeForUsersWithoutMfa() {
        // The second factor is optional: an absent code must not block a
        // user who never enrolled. The domain turns the missing code into
        // the generic authentication failure when MFA is on.
        var request = new AuthWebRequest("alice", "Secret123", null, null, null);

        assertTrue(validator.validate(request).isEmpty());
        assertTrue(totpCodeTemplates(request).isEmpty());
    }
}
