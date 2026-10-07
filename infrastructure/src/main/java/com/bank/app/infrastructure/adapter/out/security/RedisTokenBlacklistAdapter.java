package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "redis")
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String KEY_PREFIX = "token_blacklist:";
    private static final String HASHED_KEY_PREFIX = KEY_PREFIX + "sha256:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        String key = hashedKey(token);
        redisTemplate.opsForValue().set(key, "blacklisted", expirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Boolean hashedPresent = redisTemplate.hasKey(hashedKey(token));
        if (hashedPresent == null) {
            throw new IllegalStateException("Redis revocation lookup returned no result");
        }
        if (hashedPresent) {
            return true;
        }
        // Older instances wrote the bearer token into the key itself. Read those
        // keys until their existing Redis TTL expires, but never create new ones.
        Boolean legacyPresent = redisTemplate.hasKey(KEY_PREFIX + token);
        if (legacyPresent == null) {
            throw new IllegalStateException("Redis legacy revocation lookup returned no result");
        }
        return legacyPresent;
    }

    private static String hashedKey(String token) {
        return HASHED_KEY_PREFIX + TokenDigest.sha256Hex(token);
    }

    @Override
    public void cleanExpired() {
        // Redis TTL handles expiration automatically
    }
}
