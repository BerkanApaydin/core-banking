package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.common.adapter.in.api.PublicApiPaths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        List<String> paths,
        String backend,
        @DefaultValue("10") int maxRequests,
        @DefaultValue("10000") int timeWindowMs,
        @DefaultValue("120") int resourceMaxRequests,
        @DefaultValue("60000") int resourceTimeWindowMs
) {
    /**
     * Resource-tier defaults: dashboard-style authenticated traffic (accounts,
     * transfers, admin) bursts well above the auth tier. Kept as named
     * constants next to the {@code @DefaultValue} literals so a change to one
     * cannot silently diverge from the other.
     */
    public static final int DEFAULT_RESOURCE_MAX_REQUESTS = 120;
    public static final int DEFAULT_RESOURCE_TIME_WINDOW_MS = 60000;

    public RateLimitProperties {
        // Same contract as the other *Properties records: absent (null) falls
        // back to defaults, explicit values — including an empty path list
        // (kill-switch) or 0 limits — are honored untouched.
        if (paths == null) {
            paths = List.of(
                    PublicApiPaths.LOGIN,
                    PublicApiPaths.BROWSER_LOGIN,
                    PublicApiPaths.REGISTER,
                    PublicApiPaths.REFRESH,
                    PublicApiPaths.BROWSER_REFRESH,
                    PublicApiPaths.LOGOUT,
                    PublicApiPaths.BROWSER_LOGOUT,
                    PublicApiPaths.BROWSER_SESSION,
                    PublicApiPaths.ACCOUNTS,
                    PublicApiPaths.TRANSFERS,
                    PublicApiPaths.ADMIN);
        }
        if (backend == null || backend.isBlank()) {
            backend = "caffeine";
        }
    }

    /**
     * Authentication endpoints (brute-force sensitive) keep the tight
     * {@code maxRequests}/{@code timeWindowMs} budget; every other protected
     * prefix uses the looser resource budget, so a dashboard load or a NAT
     * address doing legitimate reads cannot starve on the login budget.
     *
     * <p>Single constructor on purpose: a second overload breaks Spring
     * Boot's constructor-binding discovery for this
     * {@code @ConfigurationProperties} record (the container falls back to
     * no-arg instantiation and the application fails to boot).
     */
    public boolean isAuthTier(String matchedPrefix) {
        return matchedPrefix != null && matchedPrefix.startsWith(PublicApiPaths.AUTH_PREFIX);
    }

    public int maxRequestsFor(String matchedPrefix) {
        return isAuthTier(matchedPrefix) ? maxRequests : resourceMaxRequests;
    }

    public int timeWindowMsFor(String matchedPrefix) {
        return isAuthTier(matchedPrefix) ? timeWindowMs : resourceTimeWindowMs;
    }
}
