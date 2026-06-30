package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.TokenBlacklistPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "caffeine", matchIfMissing = true)
public class TokenBlacklistAdapter implements TokenBlacklistPort {

    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String token, long expirationMs) {
        long expiry = System.currentTimeMillis() + expirationMs;
        blacklist.put(token, expiry);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    @Override
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        blacklist.values().removeIf(expiry -> now > expiry);
    }
}
