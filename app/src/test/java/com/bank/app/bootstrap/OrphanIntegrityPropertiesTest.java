package com.bank.app.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrphanIntegrityPropertiesTest {

    @Test
    void shouldRejectNegativeAlarmThreshold() {
        assertThatThrownBy(() -> new OrphanIntegrityProperties(true, "0 0 3 * * *", -1))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orphanAlarmThreshold");
    }

    @Test
    void shouldRejectBlankCron() {
        assertThatThrownBy(() -> new OrphanIntegrityProperties(true, "  ", 0))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orphanCheckCron");
    }

    @Test
    void shouldRejectNullCron() {
        assertThatThrownBy(() -> new OrphanIntegrityProperties(true, null, 0))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orphanCheckCron");
    }

    @Test
    void shouldRejectInvalidCron() {
        assertThatThrownBy(() -> new OrphanIntegrityProperties(true, "not-a-cron", 0))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orphanCheckCron");
    }

    @Test
    void shouldAcceptValidProperties() {
        OrphanIntegrityProperties properties =
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 0);

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.orphanCheckCron()).isEqualTo("0 0 3 * * *");
        assertThat(properties.orphanAlarmThreshold()).isZero();
    }

    /**
     * The reporter schedules itself with the RAW placeholder
     * {@code ${app.integrity.orphan-check-cron:...}} while the bound record
     * exposes {@code orphanCheckCron()}. If the two defaults drift apart, ops
     * sets the property believing it governs the schedule and the scan runs
     * at an unexpected hour.
     */
    @Test
    void scheduledCronPlaceholderMustUseTheBoundPropertyKeyAndDefault() throws Exception {
        Scheduled scheduled = OrphanIntegrityReporter.class
                .getMethod("reportOrphans")
                .getAnnotation(Scheduled.class);

        String placeholder = scheduled.cron();
        String key = placeholder.substring(placeholder.indexOf("${") + 2, placeholder.indexOf(':'));
        String defaultCron = placeholder.substring(placeholder.indexOf(':') + 1, placeholder.lastIndexOf('}'));
        String boundPrefix = OrphanIntegrityProperties.class
                .getAnnotation(ConfigurationProperties.class).prefix();

        assertThat(key)
                .as("the placeholder must address the property the record binds")
                .isEqualTo(boundPrefix + ".orphan-check-cron");
        assertThat(defaultCron)
                .as("the placeholder default must match the record's own default")
                .isEqualTo(boundDefaults().orphanCheckCron());
        assertThat(defaultCron).isEqualTo("0 0 3 * * *");
        assertThatNoException()
                .isThrownBy(() -> new OrphanIntegrityProperties(true, defaultCron, 0));
    }

    /**
     * The value the reporter actually schedules with is the one the record
     * binds, so ops can rely on the record to validate the configured cron
     * at startup.
     */
    @Test
    void boundDefaultMustBeTheCronTheReporterSchedulesWith() {
        assertThat(boundDefaults().orphanCheckCron())
                .isEqualTo("0 0 3 * * *");
        assertThat(boundDefaults().enabled()).isTrue();
        assertThat(boundDefaults().orphanAlarmThreshold()).isZero();
    }

    private static OrphanIntegrityProperties boundDefaults() {
        // Binder needs at least one present property under the prefix before
        // it instantiates a value object; enabling is itself default-valued.
        return new Binder(new MapConfigurationPropertySource(
                Map.of("app.integrity.enabled", "true")))
                .bind("app.integrity", OrphanIntegrityProperties.class)
                .orElseThrow(() -> new AssertionError("app.integrity must bind to defaults"));
    }
}
