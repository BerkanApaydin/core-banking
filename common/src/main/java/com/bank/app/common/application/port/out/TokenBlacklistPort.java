package com.bank.app.common.application.port.out;

public interface TokenBlacklistPort {

    void blacklist(String token, long expirationMs);

    boolean isBlacklisted(String token);

    void cleanExpired();
}
