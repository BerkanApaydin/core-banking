package com.bank.app.common.adapter.in.web;

/**
 * Rate limiting abstraction — Redis (production) veya Caffeine (dev/test) implementasyonları.
 */
public interface RateLimiter {

    /**
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String clientKey);
}
