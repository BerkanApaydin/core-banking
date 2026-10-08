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
        long doubled = Math.min(currentDelayMs * 2, maxDelayMs);
        if (jitterMs == 0) {
            return doubled;
        }
        return doubled + ThreadLocalRandom.current().nextLong(0, jitterMs + 1);
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
