package com.bank.app.transfer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(
        // S7/S8: a Duration, not a bare int — the unit travels with the value.
        // Plain numbers bind as hours via @DurationUnit, so the existing
        // TRANSFER_CANCELLATION_WINDOW_HOURS env contract keeps working, while
        // "PT30M"/"30m" now express sub-hour windows too.
        @DefaultValue("24") @DurationUnit(ChronoUnit.HOURS) Duration cancellationWindow,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500") long initialDelayMs,
        @DefaultValue("2000") long maxDelayMs,
        @DefaultValue("100") int maxPageSize
) {
    public TransferProperties {
        // Fail fast: maxAttempts < 1 would silently skip the use case body and
        // throw IllegalStateException from the retry aspect instead.
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1: " + maxAttempts);
        }
        if (cancellationWindow == null || cancellationWindow.isNegative()
                || cancellationWindow.isZero()) {
            throw new IllegalArgumentException("cancellationWindow must be positive: " + cancellationWindow);
        }
    }
}
