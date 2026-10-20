package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.LoginAttemptStoreUnavailableException;
import com.bank.app.infrastructure.adapter.in.security.LoginAttemptProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis", matchIfMissing = false)
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    private static final String IP_PREFIX = "login_attempt:ip:";
    private static final String ACCOUNT_IP_PREFIX = "login_attempt:account_ip:";
    private static final String ACCOUNT_PREFIX = "login_attempt:user:";
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

    private final StringRedisTemplate redisTemplate;    private final int maxAttempts;
    private final int globalAccountMaxAttempts;
    private final long windowMinutes;
    private final DefaultRedisScript<Long> recordFailureScript;

    public RedisLoginAttemptAdapter(
            StringRedisTemplate redisTemplate,
            LoginAttemptProperties properties) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = properties.maxAttempts();
        this.globalAccountMaxAttempts = properties.globalAccountMaxAttempts();
        this.windowMinutes = properties.windowMinutes();
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
    public boolean isUsernameBlocked(String username, String ip) {
        if (maxAttempts < 0 || username == null)
            return false;
        String normalized = normalizeUsername(username);
        return withRedis(() -> {
            // Account + IP: the tight budget.
            String pair = redisTemplate.opsForValue().get(accountIpKey(normalized, ip));
            if (pair != null && Long.parseLong(pair) >= maxAttempts) {
                return true;
            }
            // Account only: the loose budget that still stops a distributed
            // attack across many IPs.
            String account = redisTemplate.opsForValue().get(ACCOUNT_PREFIX + normalized);
            return account != null && Long.parseLong(account) >= globalAccountMaxAttempts;
        });
    }

    @Override
    public void recordFailure(String ip, String username) {
        // A null username must not create a junk "user:null" counter: only
        // the IP bucket is incremented, mirroring the Caffeine no-op.
        List<String> keys = username == null
                ? List.of(IP_PREFIX + ip)
                : List.of(IP_PREFIX + ip,
                        accountIpKey(normalizeUsername(username), ip),
                        ACCOUNT_PREFIX + normalizeUsername(username));
        Long result = withRedis(() -> redisTemplate.execute(
                recordFailureScript, keys,
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
        if (username == null) return;
        String normalized = normalizeUsername(username);
        withRedis(() -> {
            redisTemplate.delete(ACCOUNT_PREFIX + normalized);
            // Pair buckets carry the account in the key prefix, so they are
            // cleared by pattern. The username is escaped: it is user input and
            // Redis glob characters (* ? [ ] \) would otherwise widen the match
            // and clear other accounts' counters. SCAN, never KEYS: KEYS blocks
            // the Redis main thread for the whole keyspace.
            deleteByPattern(ACCOUNT_IP_PREFIX + escapeGlob(normalized) + "|*");
            return Boolean.TRUE;
        });
    }

    private void deleteByPattern(String pattern) {
        java.util.Set<String> found = new java.util.LinkedHashSet<>();
        try (org.springframework.data.redis.core.Cursor<String> cursor =
                     redisTemplate.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                             .match(pattern).count(500).build())) {
            // A null cursor is a misconfigured client, not an empty result:
            // treating it as "nothing to delete" would silently keep the
            // counter alive.
            if (cursor == null) {
                throw new IllegalStateException("Redis SCAN returned no cursor");
            }
            while (cursor.hasNext()) {
                found.add(cursor.next());
            }
        }
        if (!found.isEmpty()) {
            redisTemplate.delete(found);
        }
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    private <T> T withRedis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException | NumberFormatException ex) {
            throw new LoginAttemptStoreUnavailableException(ex);
        }
    }

    /**
     * Case-fold brute-force keys: if authentication folds case and the guard
     * does not, an attacker rotates username casing to get a fresh budget per
     * variant. Null-safe: callers pass the raw request value through.
     */
    static String normalizeUsername(String username) {
        return username == null ? null : username.toLowerCase(Locale.ROOT);
    }

    /**
     * Escapes Redis glob metacharacters so a username is matched literally
     * inside a SCAN pattern. Backslash is escaped first: it is the escape
     * character itself.
     */
    static String escapeGlob(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static String accountIpKey(String normalizedUsername, String ip) {
        return ACCOUNT_IP_PREFIX + normalizedUsername + "|" + (ip == null ? "-" : ip);
    }
}
