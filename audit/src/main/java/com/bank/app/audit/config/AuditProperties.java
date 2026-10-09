package com.bank.app.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(
        @DefaultValue("500") int maxQueryLimit,
        @DefaultValue("true") boolean retentionEnabled,
        @DefaultValue("365") long retentionDays
) {
    public AuditProperties {
        if (maxQueryLimit < 1) {
            throw new IllegalArgumentException("Audit max query limit must be at least 1: " + maxQueryLimit);
        }
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Audit retention days must be at least 1: " + retentionDays);
        }
    }
}
