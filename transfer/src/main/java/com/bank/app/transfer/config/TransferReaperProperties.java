package com.bank.app.transfer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Crash-window reaper tuning (see {@code TransferPendingReaper}).
 *
 * <p>Kept separate from {@link TransferProperties}: that record is
 * constructed positionally in tests, so widening it would break every call
 * site for an unrelated concern.
 */
@Validated
@ConfigurationProperties(prefix = "app.transfer.reaper")
public record TransferReaperProperties(
        @DefaultValue("true") boolean enabled,
        // Stale threshold: a PENDING row younger than this is still a live
        // in-flight transfer, not a crash leftover. Well above the 30s
        // use-case transaction timeout so slow (not dead) placements are
        // never reaped.
        @DefaultValue("PT15M") Duration olderThan,
        @DefaultValue("50") int batchSize,
        // Per-row REQUIRES_NEW transaction timeout (see TransferPendingReaper):
        // kept well above any single-row write, well below olderThan.
        @DefaultValue("30") int txTimeoutSeconds
) {
    public TransferReaperProperties {
        if (olderThan == null || olderThan.isNegative() || olderThan.isZero()) {
            throw new IllegalArgumentException("olderThan must be positive: " + olderThan);
        }
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("batchSize must be between 1 and 200: " + batchSize);
        }
        if (txTimeoutSeconds < 1 || txTimeoutSeconds > 300) {
            throw new IllegalArgumentException("txTimeoutSeconds must be between 1 and 300: " + txTimeoutSeconds);
        }
    }
}
