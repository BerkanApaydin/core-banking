package com.bank.app.user.application.port.out;

/**
 * Token revocation port owned by the User bounded context.
 * Implemented by infrastructure adapters (Caffeine/Redis backends).
 */
public interface TokenBlacklistPort {

    void blacklist(String token, long expirationMs);

    boolean isBlacklisted(String token);

    void cleanExpired();
}
