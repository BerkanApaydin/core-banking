package com.bank.app.user.application.port.out;

/**
 * Token lifecycle port owned by the User bounded context.
 * Implemented by infrastructure adapters (e.g. JWT provider).
 */
public interface JwtPort {
    record VerifiedToken(String username, Long userId, String role, String tokenId, long expiresAtMs) {}

    /** Returns null for an invalid, expired or incomplete signed token. */
    VerifiedToken verifyAndDecode(String token);

    String extractUsername(String token);
    String generateToken(Long userId, String username);
    String generateToken(Long userId, String username, String role);

    /**
     * Version-stamped issuance (V39): the token carries the user's current
     * generation. Refresh rotation rejects tokens minted before a role or
     * password change; pre-versioning tokens default to generation 0.
     */
    String generateToken(Long userId, String username, String role, long tokenVersion);

    /** Short-lived access token is unchanged; long-lived refresh token carries {@code typ=refresh}. */
    String generateRefreshToken(Long userId, String username, String role);

    String generateRefreshToken(Long userId, String username, String role, long tokenVersion);

    /**
     * Token generation, or 0 for tokens minted before versioning (rolling
     * deploys never lock users out). Test doubles inherit the legacy default.
     */
    default long extractTokenVersion(String token) {
        return 0L;
    }

    /**
     * {@code "access"} or {@code "refresh"}. Tokens issued before typing
     * default to {@code "access"} so rolling deploys never lock users out.
     *
     * <p>SEC-03 contract: callers must pass an already signature-verified
     * token ({@link #verifyAndDecode} first). An unverifiable token yields
     * {@code "access"} by convention so it can never escalate into a refresh
     * flow — never branch on this result alone for untrusted input.
     */
    String extractTokenType(String token);

    long getExpirationMs();
    long getRefreshExpirationMs();

    /**
     * Milliseconds until this token expires, for blacklist TTL purposes.
     * Defaults to the full expiration so existing implementations keep working;
     * providers should override with the token's actual remaining lifetime to
     * avoid retaining blacklist entries longer than the token could live.
     */
    default long getRemainingMs(String token) {
        return getExpirationMs();
    }
}
