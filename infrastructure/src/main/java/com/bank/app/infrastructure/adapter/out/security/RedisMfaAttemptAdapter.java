package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.user.config.MfaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared (multi-replica) MFA brute-force counter. Production default: the
 * budget must be identical on every pod, otherwise an attacker simply
 * spreads attempts across replicas.
 *
 * <p>The counter is keyed on the ACCOUNT, not the IP: the MFA endpoints are
 * session-authenticated, so an IP-keyed budget would let one abusive client
 * lock a legitimate user out of their own second factor.
 *
 * <p>Wired together with {@link RedisLoginAttemptAdapter} — both follow
 * {@code app.security.failed-login.backend}, so a deployment can never run
 * one shared and the other per-JVM.
 */
@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis", matchIfMissing = false)
public class RedisMfaAttemptAdapter implements MfaAttemptPort {

    private static final String KEY_PREFIX = "mfa_attempt:user:";

    /**
     * INCR + PEXPIRE in one script: a crash between the two commands would
     * otherwise leave a counter without a TTL and lock the account out
     * forever.
     */
    private static final String RECORD_FAILURE_SCRIPT = """
            local ttl = tonumber(ARGV[1])
            local count = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ttl)
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final int windowMinutes;
    private final DefaultRedisScript<Long> recordFailureScript;

    public RedisMfaAttemptAdapter(StringRedisTemplate redisTemplate, MfaProperties properties) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = properties.mfaMaxAttempts();
        this.windowMinutes = properties.mfaWindowMinutes();
        this.recordFailureScript = new DefaultRedisScript<>(RECORD_FAILURE_SCRIPT, Long.class);
    }

    @Override
    public boolean isBlocked(Long userId) {
        if (maxAttempts < 0 || userId == null) {
            return false;
        }
        return withRedis(() -> {
            String count = redisTemplate.opsForValue().get(key(userId));
            return count != null && Long.parseLong(count) >= maxAttempts;
        });
    }

    @Override
    public void recordFailure(Long userId) {
        if (userId == null) {
            return;
        }
        Long result = withRedis(() -> redisTemplate.execute(
                recordFailureScript, java.util.List.of(key(userId)),
                String.valueOf(TimeUnit.MINUTES.toMillis(windowMinutes))));
        if (result == null) {
            throw new LoginAttemptStoreUnavailableException(
                    new IllegalStateException("MFA attempt counter update returned no result"));
        }
    }

    @Override
    public void reset(Long userId) {
        if (userId == null) {
            return;
        }
        withRedisVoid(() -> redisTemplate.delete(key(userId)));
    }

    @Override
    public int getWindowMinutes() {
        return windowMinutes;
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private <T> T withRedis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException | NumberFormatException ex) {
            throw new LoginAttemptStoreUnavailableException(ex);
        }
    }

    private void withRedisVoid(Runnable operation) {
        try {
            operation.run();
        } catch (DataAccessException | NumberFormatException ex) {
            throw new LoginAttemptStoreUnavailableException(ex);
        }
    }
}
