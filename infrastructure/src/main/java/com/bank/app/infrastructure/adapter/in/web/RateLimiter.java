package com.bank.app.infrastructure.adapter.in.web;

/**
 * Rate limiting abstraction — Redis (production) or Caffeine (dev/test) implementations.
 */
public interface RateLimiter {

    /**
     * @return true if request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String clientKey);
}
