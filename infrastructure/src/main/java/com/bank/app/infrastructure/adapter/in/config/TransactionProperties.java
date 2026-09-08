package com.bank.app.infrastructure.adapter.in.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.transaction")
public record TransactionProperties(
        @DefaultValue("30") int timeoutSeconds
) {
    public TransactionProperties {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("Transaction timeout must be at least 1 second");
        }
    }
}
