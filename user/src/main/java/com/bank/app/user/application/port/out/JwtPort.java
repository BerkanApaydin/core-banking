package com.bank.app.user.application.port.out;

/**
 * Token lifecycle port owned by the User bounded context.
 * Implemented by infrastructure adapters (e.g. JWT provider).
 */
public interface JwtPort {
    String extractUsername(String token);
    String generateToken(Long userId, String username);
    String generateToken(Long userId, String username, String role);
    String extractRole(String token);
    Long extractUserId(String token);
    boolean isTokenValid(String token);
    long getExpirationMs();

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
