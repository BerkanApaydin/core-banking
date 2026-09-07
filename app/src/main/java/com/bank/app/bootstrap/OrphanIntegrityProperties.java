package com.bank.app.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.integrity")
public record OrphanIntegrityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String orphanCheckCron
) {}
