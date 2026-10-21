package com.bank.app.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.integrity")
public record OrphanIntegrityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("0 0 3 * * *") String orphanCheckCron,
        // Alarm threshold: orphan counts above this raise an ERROR log plus the
        // db.orphan.alarm counter (wire it to Alertmanager/PagerDuty; any orphan
        // means out-of-band deletion bypassed the application layer).
        @DefaultValue("0") long orphanAlarmThreshold
) {
    public OrphanIntegrityProperties {
        if (orphanCheckCron == null || orphanCheckCron.isBlank()) {
            throw new IllegalArgumentException("orphanCheckCron must not be blank");
        }
        try {
            CronExpression.parse(orphanCheckCron);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "orphanCheckCron is not a valid Spring cron expression: " + orphanCheckCron,
                    failure);
        }
        if (orphanAlarmThreshold < 0) {
            throw new IllegalArgumentException(
                    "orphanAlarmThreshold must be zero or positive: " + orphanAlarmThreshold);
        }
    }
}
