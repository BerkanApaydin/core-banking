package com.bank.app.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AuditPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AuditPropertiesConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuditProperties.class)
    static class AuditPropertiesConfig {
    }

    @Test
    void shouldRejectNonPositiveMaxQueryLimit() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new AuditProperties(0, true, 365));
        assertTrue(failure.getMessage().contains("max query limit"));
    }

    @Test
    void shouldRejectNonPositiveRetentionDays() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new AuditProperties(500, true, 0));
        assertTrue(failure.getMessage().contains("retention days"));
    }

    @Test
    void shouldKeepValidValues() {
        AuditProperties props = new AuditProperties(500, true, 365);

        assertEquals(500, props.maxQueryLimit());
        assertTrue(props.retentionEnabled());
        assertEquals(365, props.retentionDays());
    }

    @Test
    void shouldApplyTheDeclaredDefaultsWhenNoPropertyIsConfigured() {
        // The defaults are the production posture: a deployment that omits the
        // block silently falls back to a 500-row query cap and a 365-day
        // retention window.
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AuditProperties props = context.getBean(AuditProperties.class);

            assertEquals(500, props.maxQueryLimit());
            assertTrue(props.retentionEnabled());
            assertEquals(365, props.retentionDays());
        });
    }

    @Test
    void shouldBindTheConfiguredValues() {
        contextRunner.withPropertyValues(
                "app.audit.max-query-limit=50",
                "app.audit.retention-days=30",
                "app.audit.retention-enabled=false").run(context -> {
                    assertThat(context).hasNotFailed();
                    AuditProperties props = context.getBean(AuditProperties.class);

                    assertEquals(50, props.maxQueryLimit());
                    assertFalse(props.retentionEnabled());
                    assertEquals(30, props.retentionDays());
                });
    }

    @Test
    void shouldFailToStartOnANonPositiveMaxQueryLimit() {
        // A zero limit would make every audit query return nothing; the
        // application must refuse to start instead of running with it.
        contextRunner.withPropertyValues("app.audit.max-query-limit=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("max query limit");
        });
    }

    @Test
    void shouldFailToStartOnANonPositiveRetentionDays() {
        // A zero retention window would purge the whole audit trail on the
        // first scheduler run.
        contextRunner.withPropertyValues("app.audit.retention-days=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("retention days");
        });
    }

    @Test
    void shouldExposeTheRetentionSwitchAsBoundFromTheEnvironment() {
        // NOTE: the accessor is only a READ of the property. The retention job
        // itself is gated by @ConditionalOnProperty on the environment, so
        // flipping this record's field changes nothing - see the reported
        // divergence between AuditProperties and AuditRetentionScheduler.
        assertThat(new AuditProperties(500, true, 365).retentionEnabled()).isTrue();
        assertThat(new AuditProperties(500, false, 365).retentionEnabled()).isFalse();

        contextRunner.withPropertyValues("app.audit.retention-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertFalse(context.getBean(AuditProperties.class).retentionEnabled());
        });
        contextRunner.withPropertyValues("app.audit.retention-enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertTrue(context.getBean(AuditProperties.class).retentionEnabled());
        });
    }

    @Test
    void shouldReadTheRetentionSwitchWithEveryBooleanSpellingTheEnvironmentAccepts() {
        // The bound boolean accepts every Spring boolean spelling ("1",
        // "yes", "on"), while the retention job's own gate matches only the
        // literal "true". The accessor therefore reports "enabled" for values
        // that leave the job switched off - see the reported divergence
        // between AuditProperties and AuditRetentionScheduler.
        contextRunner.withPropertyValues("app.audit.retention-enabled=1").run(context -> {
            assertThat(context).hasNotFailed();
            assertTrue(context.getBean(AuditProperties.class).retentionEnabled());
        });
    }
}
