package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.token-blacklist.backend", havingValue = "redis")
public class RedisTokenBlacklistAdapter implements TokenBlacklistPort {

    private static final String KEY_PREFIX = "token_blacklist:";
    private static final String HASHED_KEY_PREFIX = KEY_PREFIX + "sha256:";

    /**
     * One round trip for the whole lookup.
     *
     * <p>The migration window needs two keys checked: the hashed key (what
     * this build writes) and the legacy key (raw bearer token, written by
     * older instances). Two separate {@code hasKey} calls meant two network
     * round trips on EVERY authenticated request, on the single shared
     * Lettuce connection. One Lua script checks both atomically and returns
     * a single answer.
     */
    private static final String LOOKUP_SCRIPT = """
            local hashed = redis.call('EXISTS', KEYS[1])
            if hashed == 1 then
                return 1
            end
            if ARGV[1] == '1' then
                local legacy = redis.call('EXISTS', KEYS[2])
                if legacy == 1 then
                    return 1
                end
            end
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> lookupScript;

    public RedisTokenBlacklistAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.lookupScript = new DefaultRedisScript<>(LOOKUP_SCRIPT, Long.class);
    }

    @Override
    public void blacklist(String token, long expirationMs) {
        String key = hashedKey(token);
        redisTemplate.opsForValue().set(key, "blacklisted", expirationMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        // SEC-05: skip oversized inputs so an attacker-controlled long string
        // can neither bloat the keyspace nor land a raw bearer in
        // monitor/slowlog output via the legacy branch. Remove this branch
        // entirely once the last legacy TTL has expired (migration window).
        boolean checkLegacy = token.length() <= 2048;
        Long present = redisTemplate.execute(lookupScript,
                java.util.List.of(hashedKey(token), KEY_PREFIX + token),
                checkLegacy ? "1" : "0");
        if (present == null) {
            throw new IllegalStateException("Redis revocation lookup returned no result");
        }
        return present > 0;
    }

    private static String hashedKey(String token) {
        return HASHED_KEY_PREFIX + TokenDigest.sha256Hex(token);
    }

    @Override
    public void cleanExpired() {
        // Redis TTL handles expiration automatically
    }
}
