package com.bank.app.user.application.port.out;

import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The identity handed back by the credential check. Its compact constructor
 * is the last line of defence against a malformed principal reaching the
 * token layer: a blank username would end up inside a JWT claim and a
 * negative token version would break the staleness comparison.
 */
@SuppressWarnings("null")
@DisplayName("AuthenticationPort.AuthenticatedUser")
class AuthenticationPortTest {

    private static final UserId ID = new UserId(7L);

    @Test
    @DisplayName("should reject a blank username")
    void shouldRejectABlankUsername() {
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(ID, "   ", Role.ROLE_USER, 0L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated username must not be blank");
    }

    @Test
    @DisplayName("should reject an empty username")
    void shouldRejectAnEmptyUsername() {
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(ID, "", Role.ROLE_USER, 0L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated username must not be blank");
    }

    @Test
    @DisplayName("should reject a negative token version")
    void shouldRejectANegativeTokenVersion() {
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(ID, "alice", Role.ROLE_USER, -1L))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token version must not be negative");
    }

    @Test
    @DisplayName("should reject a null id, username or role")
    void shouldRejectNullIdentityParts() {
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(null, "alice", Role.ROLE_USER, 0L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Authenticated user ID must not be null");
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(ID, null, Role.ROLE_USER, 0L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Authenticated username must not be null");
        assertThatThrownBy(() -> new AuthenticationPort.AuthenticatedUser(ID, "alice", null, 0L))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Authenticated role must not be null");
    }

    @Test
    @DisplayName("should expose the authenticated identity")
    void shouldExposeTheIdentity() {
        AuthenticationPort.AuthenticatedUser user =
                new AuthenticationPort.AuthenticatedUser(ID, "alice", Role.ROLE_USER, 3L, true);

        assertThat(user.id()).isEqualTo(ID);
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.role()).isEqualTo(Role.ROLE_USER);
        assertThat(user.tokenVersion()).isEqualTo(3L);
        assertThat(user.mfaEnabled()).isTrue();
    }

    @Test
    @DisplayName("should default the pre-MFA shape to mfaEnabled=false and the legacy shape to version 0")
    void shouldApplyTheConvenienceShapes() {
        AuthenticationPort.AuthenticatedUser preMfa =
                new AuthenticationPort.AuthenticatedUser(ID, "alice", Role.ROLE_USER, 2L);
        AuthenticationPort.AuthenticatedUser legacy =
                new AuthenticationPort.AuthenticatedUser(ID, "alice", Role.ROLE_USER);

        assertThat(preMfa.mfaEnabled()).isFalse();
        assertThat(preMfa.tokenVersion()).isEqualTo(2L);
        assertThat(legacy.mfaEnabled()).isFalse();
        assertThat(legacy.tokenVersion()).isZero();
    }
}
