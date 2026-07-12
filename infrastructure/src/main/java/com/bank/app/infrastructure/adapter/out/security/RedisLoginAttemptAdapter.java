package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.LoginAttemptPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "redis", matchIfMissing = false)
public class RedisLoginAttemptAdapter implements LoginAttemptPort {

    private static final String IP_PREFIX = "login_attempt:ip:";
    private static final String USERNAME_PREFIX = "login_attempt:user:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final long windowMinutes;

    public RedisLoginAttemptAdapter(
            StringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Value("${app.security.failed-login.max-attempts:5}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${app.security.failed-login.window-minutes:15}") long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
    }

    @Override
    public boolean isIpBlocked(String ip) {
        if (maxAttempts < 0)
            return false;
        String count = redisTemplate.opsForValue().get(IP_PREFIX + ip);
        return count != null && Integer.parseInt(count) >= maxAttempts;
    }

    @Override
    public boolean isUsernameBlocked(String username) {
        if (maxAttempts < 0)
            return false;
        String count = redisTemplate.opsForValue().get(USERNAME_PREFIX + username);
        return count != null && Integer.parseInt(count) >= maxAttempts;
    }

    @Override
    public void recordFailure(String ip, String username) {
        String ipKey = IP_PREFIX + ip;
        String userKey = USERNAME_PREFIX + username;
        redisTemplate.opsForValue().increment(ipKey);
        redisTemplate.expire(ipKey, windowMinutes, TimeUnit.MINUTES);
        redisTemplate.opsForValue().increment(userKey);
        redisTemplate.expire(userKey, windowMinutes, TimeUnit.MINUTES);
    }

    @Override
    public void reset(String ip) {
        redisTemplate.delete(IP_PREFIX + ip);
    }

    @Override
    public void resetByUsername(String username) {
        redisTemplate.delete(USERNAME_PREFIX + username);
    }

    @Override
    public int getWindowMinutes() {
        return (int) windowMinutes;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
