package com.bank.app.transfer.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.transfer")
public record TransferProperties(
        @DefaultValue("24") int cancellationWindowHours,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("500") long initialDelayMs,
        @DefaultValue("2000") long maxDelayMs
) {}
