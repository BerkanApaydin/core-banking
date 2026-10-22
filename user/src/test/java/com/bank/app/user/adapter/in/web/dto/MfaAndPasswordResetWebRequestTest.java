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
 * Web contracts for the MFA management and password-reset endpoints. The
 * constraints are the first line of defence: a malformed TOTP code or a
 * missing mandatory value must be rejected at the edge, before it reaches a
 * use case where it would be charged to a user's failure budget.
 */
@SuppressWarnings("null")
@DisplayName("MFA and password-reset web requests")
class MfaAndPasswordResetWebRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messageTemplatesFor(Object request, String property) {
        return validator.validate(request).stream()
                .filter(violation -> property.equals(violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessageTemplate)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("MfaEnableWebRequest: a valid password produces no violation")
    void shouldAcceptAValidEnableRequest() {
        var request = new MfaEnableWebRequest("Str0ngPassw0rd!");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.password()).isEqualTo("Str0ngPassw0rd!");
    }

    @Test
    @DisplayName("MfaEnableWebRequest: a blank re-authentication password is rejected")
    void shouldRejectABlankEnablePassword() {
        var request = new MfaEnableWebRequest("   ");

        assertThat(messageTemplatesFor(request, "password"))
                .containsExactly("{validation.password.required}");
    }

    @Test
    @DisplayName("MfaEnableWebRequest: a null password is rejected")
    void shouldRejectANullEnablePassword() {
        var request = new MfaEnableWebRequest(null);

        assertThat(messageTemplatesFor(request, "password"))
                .containsExactly("{validation.password.required}");
    }

    @Test
    @DisplayName("MfaEnableWebRequest: an overlong password is rejected")
    void shouldRejectAnOverlongEnablePassword() {
        var request = new MfaEnableWebRequest("A1" + "x".repeat(71));

        assertThat(messageTemplatesFor(request, "password"))
                .containsExactly("{validation.password.tooLong}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a valid password and code produce no violation")
    void shouldAcceptAValidConfirmRequest() {
        var request = new MfaConfirmWebRequest("Str0ngPassw0rd!", "123456");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.password()).isEqualTo("Str0ngPassw0rd!");
        assertThat(request.code()).isEqualTo("123456");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a blank password is rejected")
    void shouldRejectABlankConfirmPassword() {
        var request = new MfaConfirmWebRequest("", "123456");

        assertThat(messageTemplatesFor(request, "password"))
                .containsExactly("{validation.password.required}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a blank code is rejected")
    void shouldRejectABlankConfirmCode() {
        var request = new MfaConfirmWebRequest("Str0ngPassw0rd!", "  ");

        assertThat(messageTemplatesFor(request, "code"))
                .containsOnly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a null code is rejected")
    void shouldRejectANullConfirmCode() {
        var request = new MfaConfirmWebRequest("Str0ngPassw0rd!", null);

        assertThat(messageTemplatesFor(request, "code"))
                .containsExactly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a non-numeric code is rejected")
    void shouldRejectANonNumericConfirmCode() {
        var request = new MfaConfirmWebRequest("Str0ngPassw0rd!", "abcdef");

        assertThat(messageTemplatesFor(request, "code"))
                .containsOnly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: a code outside the 6-8 digit range is rejected")
    void shouldRejectAConfirmCodeOutsideTheDigitRange() {
        assertThat(messageTemplatesFor(new MfaConfirmWebRequest("Str0ngPassw0rd!", "12345"), "code"))
                .containsOnly("{validation.totp.invalid}");
        assertThat(messageTemplatesFor(new MfaConfirmWebRequest("Str0ngPassw0rd!", "123456789"), "code"))
                .containsOnly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("MfaConfirmWebRequest: the 8-digit code is accepted")
    void shouldAcceptAnEightDigitConfirmCode() {
        var request = new MfaConfirmWebRequest("Str0ngPassw0rd!", "12345678");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("MfaDisableWebRequest: a valid password and code produce no violation")
    void shouldAcceptAValidDisableRequest() {
        var request = new MfaDisableWebRequest("Str0ngPassw0rd!", "123456");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.password()).isEqualTo("Str0ngPassw0rd!");
        assertThat(request.code()).isEqualTo("123456");
    }

    @Test
    @DisplayName("MfaDisableWebRequest: a blank password is rejected")
    void shouldRejectABlankDisablePassword() {
        var request = new MfaDisableWebRequest("", "123456");

        assertThat(messageTemplatesFor(request, "password"))
                .containsExactly("{validation.password.required}");
    }

    @Test
    @DisplayName("MfaDisableWebRequest: a blank code is rejected")
    void shouldRejectABlankDisableCode() {
        var request = new MfaDisableWebRequest("Str0ngPassw0rd!", "");

        assertThat(messageTemplatesFor(request, "code"))
                .containsOnly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("MfaDisableWebRequest: a non-numeric code is rejected")
    void shouldRejectANonNumericDisableCode() {
        var request = new MfaDisableWebRequest("Str0ngPassw0rd!", "12a456");

        assertThat(messageTemplatesFor(request, "code"))
                .containsOnly("{validation.totp.invalid}");
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: a valid token and password produce no violation")
    void shouldAcceptAValidResetConfirmRequest() {
        var request = new PasswordResetConfirmWebRequest("reset-token", "Str0ngPassw0rd!");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.token()).isEqualTo("reset-token");
        assertThat(request.newPassword()).isEqualTo("Str0ngPassw0rd!");
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: a blank token is rejected")
    void shouldRejectABlankResetToken() {
        var request = new PasswordResetConfirmWebRequest("  ", "Str0ngPassw0rd!");

        assertThat(messageTemplatesFor(request, "token"))
                .containsExactly("{validation.token.required}");
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: a null token is rejected")
    void shouldRejectANullResetToken() {
        var request = new PasswordResetConfirmWebRequest(null, "Str0ngPassw0rd!");

        assertThat(messageTemplatesFor(request, "token"))
                .containsExactly("{validation.token.required}");
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: a blank new password is rejected")
    void shouldRejectABlankNewPassword() {
        var request = new PasswordResetConfirmWebRequest("reset-token", "   ");

        assertThat(messageTemplatesFor(request, "newPassword"))
                .containsExactly("{validation.password.required}");
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: a weak new password is rejected")
    void shouldRejectAWeakNewPassword() {
        // The live policy runs here: a reset must not install a password the
        // registration endpoint would have refused.
        var request = new PasswordResetConfirmWebRequest("reset-token", "short");

        assertThat(messageTemplatesFor(request, "newPassword"))
                .anySatisfy(template -> assertThat(template).contains("at least 12 characters"));
    }

    @Test
    @DisplayName("PasswordResetConfirmWebRequest: an overlong new password is rejected")
    void shouldRejectAnOverlongNewPassword() {
        var request = new PasswordResetConfirmWebRequest("reset-token", "A1" + "x".repeat(71));

        assertThat(messageTemplatesFor(request, "newPassword"))
                .contains("{validation.password.tooLong}");
    }

    @Test
    @DisplayName("PasswordResetRequestWebRequest: a valid username produces no violation")
    void shouldAcceptAValidResetRequest() {
        var request = new PasswordResetRequestWebRequest("alice");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.username()).isEqualTo("alice");
    }

    @Test
    @DisplayName("PasswordResetRequestWebRequest: a blank username is rejected")
    void shouldRejectABlankResetUsername() {
        var request = new PasswordResetRequestWebRequest("   ");

        assertThat(messageTemplatesFor(request, "username"))
                .containsExactly("{validation.username.required}");
    }

    @Test
    @DisplayName("PasswordResetRequestWebRequest: a null username is rejected")
    void shouldRejectANullResetUsername() {
        var request = new PasswordResetRequestWebRequest(null);

        assertThat(messageTemplatesFor(request, "username"))
                .containsExactly("{validation.username.required}");
    }

    @Test
    @DisplayName("PasswordResetRequestWebRequest: an overlong username is rejected")
    void shouldRejectAnOverlongResetUsername() {
        var request = new PasswordResetRequestWebRequest("u".repeat(256));

        assertThat(messageTemplatesFor(request, "username"))
                .containsExactly("{validation.username.tooLong}");
    }

    @Test
    @DisplayName("MfaStatusResponse: exposes the enrollment flag")
    void shouldExposeTheEnrollmentFlag() {
        assertThat(new MfaStatusResponse(true).mfaEnabled()).isTrue();
        assertThat(new MfaStatusResponse(false).mfaEnabled()).isFalse();
    }
}
