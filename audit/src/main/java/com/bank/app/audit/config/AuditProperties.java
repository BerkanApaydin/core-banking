package com.bank.app.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(
        @DefaultValue("500") int maxQueryLimit
) {
    public AuditProperties {
        if (maxQueryLimit < 1) {
            throw new IllegalArgumentException("Audit max query limit must be at least 1: " + maxQueryLimit);
        }
    }
}
