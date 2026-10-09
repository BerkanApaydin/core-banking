package com.bank.app.infrastructure.adapter.in.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Crash-window reaper tuning for HTTP idempotency reservations (see
 * {@code IdempotencyPendingReaper}).
 *
 * <p>Kept separate from {@link IdempotencyProperties}: that record is
 * constructed positionally in tests, so widening it would break every call
 * site for an unrelated concern (same split as
 * {@code TransferReaperProperties} vs {@code TransferProperties}).
 */
@Validated
@ConfigurationProperties(prefix = "app.idempotency.reaper")
public record IdempotencyReaperProperties(
        @DefaultValue("true") boolean enabled,
        // Stale threshold: a PENDING row younger than this is still a live
        // in-flight request, not a crash leftover. Well above the 30s
        // use-case transaction timeout so slow (not dead) requests are
        // never reaped.
        @DefaultValue("PT15M") Duration olderThan
) {
    public IdempotencyReaperProperties {
        if (olderThan == null || olderThan.isNegative() || olderThan.isZero()) {
            throw new IllegalArgumentException("olderThan must be positive: " + olderThan);
        }
    }
}
