package com.bank.app.user.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The enrollment material handed to the client during MFA enrollment. A null
 * secret or URI would be rendered as "no secret" in the authenticator app and
 * the enrollment would silently never complete.
 */
@SuppressWarnings("null")
@DisplayName("MfaEnrollment")
class MfaEnrollmentTest {

    private static final String URI = "otpauth://totp/bank:alice?secret=JBSWY3DPEHPK3PXP";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    @DisplayName("should expose the provisioning URI and the base32 secret")
    void shouldExposeBothValues() {
        MfaEnrollment enrollment = new MfaEnrollment(URI, SECRET);

        assertThat(enrollment.otpauthUri()).isEqualTo(URI);
        assertThat(enrollment.base32Secret()).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("should reject a null base32 secret")
    void shouldRejectANullSecret() {
        assertThatThrownBy(() -> new MfaEnrollment(URI, null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Base32 secret must not be null");
    }

    @Test
    @DisplayName("should reject a null provisioning URI")
    void shouldRejectANullUri() {
        assertThatThrownBy(() -> new MfaEnrollment(null, SECRET))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("otpauth URI must not be null");
    }
}
