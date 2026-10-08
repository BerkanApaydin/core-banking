package com.bank.app.user.application.port.out;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Default-method contract for the token lifecycle port.
 *
 * <p>PIT reported {@code getRemainingMs} as NO_COVERAGE: rolling-deploy
 * doubles inherit the default, so the delegation to the full expiration must
 * be pinned.
 */
class JwtPortDefaultsTest {

    private final JwtPort minimal = new JwtPort() {
        @Override public VerifiedToken verifyAndDecode(String token) { return null; }
        @Override public String extractUsername(String token) { return null; }
        @Override public String generateToken(Long userId, String username) { return null; }
        @Override public String generateToken(Long userId, String username, String role) { return null; }
        @Override public String generateToken(Long userId, String username, String role, long tokenVersion) { return null; }
        @Override public String generateRefreshToken(Long userId, String username, String role) { return null; }
        @Override public String generateRefreshToken(Long userId, String username, String role, long tokenVersion) { return null; }
        @Override public String extractTokenType(String token) { return "access"; }
        @Override public long getExpirationMs() { return 900000L; }
        @Override public long getRefreshExpirationMs() { return 604800000L; }
    };

    @Test
    void remainingDefaultsToFullExpiration() {
        assertThat(minimal.getRemainingMs("any-token")).isEqualTo(900000L);
    }

    @Test
    void versionDefaultsToZeroForLegacyTokens() {
        assertThat(minimal.extractTokenVersion("any-token")).isZero();
    }
}
