package com.bank.app.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.integrity")
public record OrphanIntegrityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String orphanCheckCron,
        // Alarm threshold: orphan counts above this raise an ERROR log plus the
        // db.orphan.alarm counter (wire it to Alertmanager/PagerDuty; any orphan
        // means out-of-band deletion bypassed the application layer).
        @DefaultValue("0") long orphanAlarmThreshold
) {}
