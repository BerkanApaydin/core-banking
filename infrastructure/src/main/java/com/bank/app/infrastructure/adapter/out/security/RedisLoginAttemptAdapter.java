package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis", matchIfMissing = false)
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    private static final String IP_PREFIX = "login_attempt:ip:";
    private static final String USERNAME_PREFIX = "login_attempt:user:";
    private static final String RECORD_FAILURE_SCRIPT = """
            local ttl = tonumber(ARGV[1])
            for i = 1, #KEYS do
                local count = redis.call('GET', KEYS[i])
                if count and not string.match(count, '^%d+$') then
                    return redis.error_reply('invalid login attempt counter')
                end
            end
            for i = 1, #KEYS do
                redis.call('INCR', KEYS[i])
                redis.call('PEXPIRE', KEYS[i], ttl)
            end
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final long windowMinutes;
    private final DefaultRedisScript<Long> recordFailureScript;

    public RedisLoginAttemptAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.failed-login.max-attempts:5}") int maxAttempts,
            @Value("${app.security.failed-login.window-minutes:15}") long windowMinutes) {
        if (windowMinutes <= 0 || windowMinutes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Failed-login window must be a positive number of minutes");
        }
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.recordFailureScript = new DefaultRedisScript<>(RECORD_FAILURE_SCRIPT, Long.class);
    }

    @Override
    public boolean isIpBlocked(String ip) {
        if (maxAttempts < 0)
            return false;
        return withRedis(() -> {
            String count = redisTemplate.opsForValue().get(IP_PREFIX + ip);
            return count != null && Long.parseLong(count) >= maxAttempts;
        });
    }

    @Override
    public boolean isUsernameBlocked(String username) {
        if (maxAttempts < 0)
            return false;
        return withRedis(() -> {
            String count = redisTemplate.opsForValue().get(USERNAME_PREFIX + username);
            return count != null && Long.parseLong(count) >= maxAttempts;
        });
    }

    @Override
    public void recordFailure(String ip, String username) {
        String ipKey = IP_PREFIX + ip;
        String userKey = USERNAME_PREFIX + username;
        Long result = withRedis(() -> redisTemplate.execute(
                recordFailureScript, List.of(ipKey, userKey),
                String.valueOf(TimeUnit.MINUTES.toMillis(windowMinutes))));
        if (!Long.valueOf(1L).equals(result)) {
            throw new LoginAttemptStoreUnavailableException(
                    new IllegalStateException("Failed-login counter update returned no result"));
        }
    }

    @Override
    public void reset(String ip) {
        withRedis(() -> redisTemplate.delete(IP_PREFIX + ip));
    }

    @Override
    public void resetByUsername(String username) {
        withRedis(() -> redisTemplate.delete(USERNAME_PREFIX + username));
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    private <T> T withRedis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException | NumberFormatException ex) {
            throw new LoginAttemptStoreUnavailableException(ex);
        }
    }
}
