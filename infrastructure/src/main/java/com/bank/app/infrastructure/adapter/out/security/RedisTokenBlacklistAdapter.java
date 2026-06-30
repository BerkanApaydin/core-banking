package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.TokenBlacklistPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "redis")
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String KEY_PREFIX = "token_blacklist:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        String key = KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", expirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }

    @Override
    public void cleanExpired() {
        // Redis TTL handles expiration automatically
    }
}
