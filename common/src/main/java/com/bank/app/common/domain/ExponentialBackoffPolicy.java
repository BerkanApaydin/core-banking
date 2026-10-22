package com.bank.app.common.domain;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure delay policy for bounded retries. Computes the next backoff without
 * sleeping, so request threads can delegate the wait to a worker pool and
 * unit tests can assert the sequence deterministically.
 */
public final class ExponentialBackoffPolicy {

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final long jitterMs;

    public ExponentialBackoffPolicy(long initialDelayMs, long maxDelayMs, long jitterMs) {
        if (initialDelayMs <= 0) throw new IllegalArgumentException("initialDelayMs must be positive");
        if (maxDelayMs < initialDelayMs) throw new IllegalArgumentException("maxDelayMs must cover initialDelayMs");
        if (jitterMs < 0) throw new IllegalArgumentException("jitterMs must not be negative");
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitterMs = jitterMs;
    }

    public long initialDelayMs() {
        return initialDelayMs;
    }

    public long nextDelay(long currentDelayMs) {
        // Saturating double: a huge current delay must cap at maxDelayMs, not
        // overflow to a negative number and disable the wait entirely.
        long doubled = currentDelayMs > maxDelayMs / 2
                ? maxDelayMs
                : Math.min(currentDelayMs * 2, maxDelayMs);
        if (jitterMs == 0) {
            return doubled;
        }
        // jitterMs + 1 overflows when jitterMs is Long.MAX_VALUE; nextLong(bound)
        // with the bound itself still yields a non-negative sample.
        long jitterBound = jitterMs == Long.MAX_VALUE ? Long.MAX_VALUE : jitterMs + 1;
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        // The contract allows jitter to exceed maxDelayMs, but the addition must
        // not wrap around.
        try {
            return Math.addExact(doubled, jitter);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExponentialBackoffPolicy other)) return false;
        return initialDelayMs == other.initialDelayMs
                && maxDelayMs == other.maxDelayMs
                && jitterMs == other.jitterMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(initialDelayMs, maxDelayMs, jitterMs);
    }
}
