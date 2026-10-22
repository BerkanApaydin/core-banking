package com.bank.app.user.application.port.out;

import com.bank.app.user.application.port.out.PasswordResetTokenPort.PasswordResetToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The single-use predicate that decides whether a password-reset link is
 * honoured. It is the last gate before a password change, so every branch
 * (used, expired, exactly-at-expiry) is a security decision.
 */
@SuppressWarnings("null")
@DisplayName("PasswordResetToken.isUsableAt")
class PasswordResetTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0, 0);

    private static PasswordResetToken token(LocalDateTime expiresAt, LocalDateTime usedAt) {
        return new PasswordResetToken("hash", 7L, expiresAt, usedAt);
    }

    @Test
    @DisplayName("should accept a fresh, unused token")
    void shouldAcceptAFreshUnusedToken() {
        PasswordResetToken fresh = token(NOW.plusMinutes(15), null);

        assertThat(fresh.isUsableAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("should reject an already-used token even when it has not expired")
    void shouldRejectAnAlreadyUsedToken() {
        // "used at" must always win: a consumed link stays dead even inside
        // its validity window, otherwise a leaked link could be replayed.
        PasswordResetToken used = token(NOW.plusMinutes(15), NOW.minusMinutes(1));

        assertThat(used.isUsableAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("should reject an expired token")
    void shouldRejectAnExpiredToken() {
        PasswordResetToken expired = token(NOW.minusSeconds(1), null);

        assertThat(expired.isUsableAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("should reject a token expiring exactly at the current instant")
    void shouldRejectTheExactExpiryBoundary() {
        // The rule is strictly "expiresAt > now": a token whose expiry equals
        // the evaluation instant is already dead.
        PasswordResetToken boundary = token(NOW, null);

        assertThat(boundary.isUsableAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("should accept a token expiring one nanosecond later")
    void shouldAcceptATokenExpiringOneNanosecondLater() {
        PasswordResetToken justAlive = token(NOW.plusNanos(1), null);

        assertThat(justAlive.isUsableAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("should accept a token expiring one second later")
    void shouldAcceptATokenExpiringOneSecondLater() {
        PasswordResetToken alive = token(NOW.plusSeconds(1), null);

        assertThat(alive.isUsableAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("should treat a null used-at as unused")
    void shouldTreatNullUsedAtAsUnused() {
        PasswordResetToken neverUsed = token(NOW.plusMinutes(15), null);

        assertThat(neverUsed.isUsableAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("should reject a null expiry at construction time")
    void shouldRejectANullExpiry() {
        // A token without an expiry must never be treated as immortal.
        assertThatThrownBy(() -> new PasswordResetToken("hash", 7L, null, null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Expiry must not be null");
    }

    @Test
    @DisplayName("should reject a null token hash and a null user id at construction time")
    void shouldRejectNullIdentityParts() {
        assertThatThrownBy(() -> new PasswordResetToken(null, 7L, NOW.plusMinutes(15), null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Token hash must not be null");

        assertThatThrownBy(() -> new PasswordResetToken("hash", null, NOW.plusMinutes(15), null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("User ID must not be null");
    }
}
