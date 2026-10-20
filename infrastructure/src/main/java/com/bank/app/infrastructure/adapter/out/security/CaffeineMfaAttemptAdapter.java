package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.config.MfaProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local (single-JVM) MFA brute-force counter. Dev/test default: the budget is
 * per JVM, so a multi-replica deployment MUST use the Redis backend
 * (production pins {@code app.security.failed-login.backend=redis}, which
 * selects {@link RedisMfaAttemptAdapter}).
 *
 * <p>The counter is keyed on the ACCOUNT, not the IP: the MFA endpoints are
 * session-authenticated, so an IP-keyed budget would let one abusive client
 * lock a legitimate user out of their own second factor.
 */
@Component
@ConditionalOnProperty(name = "app.security.failed-login.backend", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineMfaAttemptAdapter implements MfaAttemptPort {

    private final Cache<Long, AtomicInteger> attempts;
    private final int maxAttempts;
    private final int windowMinutes;

    public CaffeineMfaAttemptAdapter(MfaProperties properties) {
        this.maxAttempts = properties.mfaMaxAttempts();
        this.windowMinutes = properties.mfaWindowMinutes();
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(properties.mfaCacheMaxSize())
                .build();
    }

    @Override
    public boolean isBlocked(Long userId) {
        if (maxAttempts < 0 || userId == null) {
            return false;
        }
        AtomicInteger count = attempts.getIfPresent(userId);
        return count != null && count.get() >= maxAttempts;
    }

    @Override
    public void recordFailure(Long userId) {
        // Null keys crash Caffeine: a missing user id must be a no-op, never
        // an NPE that masks the authentication result.
        if (userId == null) {
            return;
        }
        attempts.asMap().compute(userId, (k, v) -> {
            if (v == null) {
                return new AtomicInteger(1);
            }
            v.incrementAndGet();
            return v;
        });
    }

    @Override
    public void reset(Long userId) {
        if (userId == null) {
            return;
        }
        attempts.invalidate(userId);
    }

    @Override
    public int getWindowMinutes() {
        return windowMinutes;
    }
}
