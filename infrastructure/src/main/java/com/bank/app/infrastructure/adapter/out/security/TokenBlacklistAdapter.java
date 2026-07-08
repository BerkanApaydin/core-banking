package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.TokenBlacklistPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "caffeine", matchIfMissing = true)
public class TokenBlacklistAdapter implements TokenBlacklistPort {

    private final Cache<String, Boolean> blacklist;

    public TokenBlacklistAdapter() {
        this.blacklist = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.DAYS)
                .maximumSize(1_000_000)
                .build();
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        if (expirationMs <= 0) {
            return;
        }
        blacklist.put(token, Boolean.TRUE);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return blacklist.getIfPresent(token) != null;
    }

    @Override
    public void cleanExpired() {
        blacklist.cleanUp();
    }
}
