package com.bank.app.infrastructure.adapter.in.web;

/**
 * Rate limiting abstraction — Redis (production) or Caffeine (dev/test) implementations.
 */
public interface RateLimiter {

    /**
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String clientKey);

    /**
     * Tiered acquisition: the caller (filter) selects the budget from the
     * matched prefix, so auth endpoints keep a tight brute-force budget while
     * authenticated resource reads get a looser one.
     *
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String clientKey, int maxRequests, long timeWindowMs);
}
