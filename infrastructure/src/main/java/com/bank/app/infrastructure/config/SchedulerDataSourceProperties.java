package com.bank.app.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Optional separate datasource for maintenance schedulers (F2).
 *
 * <p>When {@link #url()} is blank the scheduler beans fall back to the primary
 * datasource, so existing deployments are unchanged. A least-privilege
 * deployment points this at {@code bank_scheduler}, a login role with
 * {@code SELECT} on {@code ledger_entries} only.
 */
@Validated
@ConfigurationProperties(prefix = "app.scheduler.datasource")
public record SchedulerDataSourceProperties(
        String url,
        String username,
        String password,
        @DefaultValue("2") int maximumPoolSize,
        @DefaultValue("true") boolean readOnly
) {
    public SchedulerDataSourceProperties {
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("Scheduler datasource pool size must be at least 1");
        }
    }

    public boolean configured() {
        return url != null && !url.isBlank();
    }
}
