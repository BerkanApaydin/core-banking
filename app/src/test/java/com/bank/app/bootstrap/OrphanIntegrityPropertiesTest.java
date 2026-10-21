package com.bank.app.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
}
