package com.bank.app.bootstrap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanIntegrityReporterTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private MeterRegistry meterRegistry;

    private void stubGauges() {
        when(meterRegistry.gauge(anyString(), any(Iterable.class), any(AtomicLong.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void shouldRunThreeChecksWhenClean() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldReportWithoutDeletingWhenOrphansExist() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldExposeCountsAsGauges() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var gaugeCaptor = ArgumentCaptor.forClass(AtomicLong.class);

        OrphanIntegrityReporter.forTests(jdbc, meterRegistry).reportOrphans();

        verify(meterRegistry, times(3)).gauge(anyString(), any(Iterable.class), gaugeCaptor.capture());
        assertThatCapturedGaugesAre(gaugeCaptor, 2L);
    }

    @Test
    void shouldTolerateNullCounts() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> null);

        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldStartWithoutMeterRegistry() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, null);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    private static double alarmCount(SimpleMeterRegistry registry, String type) {
        return registry.get("db.orphan.alarm").tag("type", type).counter().count();
    }

    private static void assertThatCapturedGaugesAre(
            ArgumentCaptor<AtomicLong> gaugeCaptor, long expected) {
        assertEquals(3, gaugeCaptor.getAllValues().size());
        for (AtomicLong gauge : gaugeCaptor.getAllValues()) {
            assertEquals(expected, gauge.get());
        }
    }

    @Test
    void shouldLogErrorWhenOrphansExceedThreshold() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        assertNotNull(findEventAtLevel(Level.ERROR));
    }

    @Test
    void shouldLogWarnWhenOrphansBelowThreshold() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        Logger logger =
                (Logger) LoggerFactory.getLogger(OrphanIntegrityReporter.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OrphanIntegrityReporter.forTests(jdbc, meterRegistry,
                    new OrphanIntegrityProperties(true, "0 0 3 * * *", 5)).reportOrphans();
        } finally {
            logger.detachAppender(appender);
        }

        assertNotNull(appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .findFirst()
                .orElse(null));
        assertNull(appender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElse(null));
    }

    @Test
    void shouldIncrementAlarmCounterWhenThresholdExceeded() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var registry = new SimpleMeterRegistry();

        OrphanIntegrityReporter.forTests(jdbc, registry,
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 0)).reportOrphans();

        // 2 orphans x 3 checks, all above threshold 0, each tagged by type.
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
        assertEquals(6.0,
                alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER)
                        + alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER)
                        + alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    @Test
    void shouldNotIncrementAlarmCounterWhenBelowThreshold() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var registry = new SimpleMeterRegistry();

        OrphanIntegrityReporter.forTests(jdbc, registry,
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 5)).reportOrphans();

        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    @Test
    void shouldUseZeroThresholdWhenPropertiesAreMissing() {
        // The canonical constructor tolerates a missing properties bean at the
        // boundary; a null threshold must fail closed to alarm-on-any-orphan
        // rather than silently disabling alerting.
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var registry = new SimpleMeterRegistry();
        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(
                jdbc, registry, null, AdvisorySchedulerLock.alwaysRun());

        reporter.reportOrphans();

        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    @Test
    void shouldNotAlarmWhenOrphansEqualThreshold() {
        // Kills the ConditionalsBoundary mutant (> vs >=): exactly-at-
        // threshold is a warning, not an alarm.
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);
        var registry = new SimpleMeterRegistry();

        OrphanIntegrityReporter.forTests(jdbc, registry,
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 5)).reportOrphans();

        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    @Test
    void shouldExposeSuccessfulScanTimeOnlyAfterAllQueriesComplete() {
        var registry = new SimpleMeterRegistry();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, registry);
        assertEquals(0.0, registry.get("db.orphan.last_success_epoch_seconds").gauge().value());

        reporter.reportOrphans();

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
        assertTrue(
                registry.get("db.orphan.last_success_epoch_seconds").gauge().value() > 0);
    }

    @Test
    void shouldNotReportSuccessWhenAnyQueryFails() {
        var registry = new SimpleMeterRegistry();
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(0L, 0L)
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, registry);

        assertThrows(
                DataAccessResourceFailureException.class, reporter::reportOrphans);
        assertEquals(0.0, registry.get("db.orphan.last_success_epoch_seconds").gauge().value());
    }

    @Test
    void shouldNotLogWarnWhenClean() {
        stubGauges();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        assertNull(findWarnEvent());
    }

    private ILoggingEvent findWarnEvent() {
        return findEventAtLevel(Level.WARN);
    }

    private ILoggingEvent findEventAtLevel(Level level) {
        Logger logger =
                (Logger) LoggerFactory.getLogger(OrphanIntegrityReporter.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            OrphanIntegrityReporter.forTests(jdbc, meterRegistry).reportOrphans();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(e -> e.getLevel() == level)
                .findFirst()
                .orElse(null);
    }

    /**
     * A replica that lost the advisory lock must not scan at all. When every
     * replica scans, the three orphan queries run N times (N x DB load on the
     * HPA-scaled 2-6 pods) and the PagerDuty-wired alarm counter is
     * incremented N times for a single real condition.
     */
    @Test
    void shouldNotScanOrAlarmWhenThisReplicaIsNotTheLeader() {
        var registry = new SimpleMeterRegistry();
        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(
                jdbc, registry, new OrphanIntegrityProperties(true, "0 0 3 * * *", 0),
                new LosingReplicaLock());

        reporter.reportOrphans();

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class));
        assertEquals(0.0, gaugeValue(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(0.0, gaugeValue(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(0.0, gaugeValue(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
        assertEquals(0.0,
                registry.get("db.orphan.last_success_epoch_seconds").gauge().value());
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(0.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    /**
     * The alarm counter is bumped by the check that succeeded, even though
     * the scan as a whole failed — so a failed scan still pages.
     */
    @Test
    void shouldStillCountTheAlarmOfTheCheckThatSucceededBeforeTheFailure() {
        var registry = new SimpleMeterRegistry();
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(1L, 2L, 3L, 9L)
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, registry);

        reporter.reportOrphans();
        assertThrows(
                DataAccessResourceFailureException.class, reporter::reportOrphans);

        assertEquals(10.0, alarmCount(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(2.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(3.0, alarmCount(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
    }

    @Test
    void shouldKeepPreviousGaugesWhenAScanFailsHalfWay() {
        var registry = new SimpleMeterRegistry();
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(5L, 6L, 7L)
                .thenReturn(9L)
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        OrphanIntegrityReporter reporter = OrphanIntegrityReporter.forTests(jdbc, registry);

        reporter.reportOrphans();
        double lastSuccess =
                registry.get("db.orphan.last_success_epoch_seconds").gauge().value();
        assertThrows(
                DataAccessResourceFailureException.class, reporter::reportOrphans);

        // All-or-nothing: a fresh 9 next to stale 6/7 would look like a real
        // account-orphan surge and would hide that the other reads failed.
        assertEquals(5.0, gaugeValue(registry, OrphanIntegrityReporter.ACCOUNTS_WITHOUT_USER));
        assertEquals(6.0, gaugeValue(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_SENDER));
        assertEquals(7.0, gaugeValue(registry, OrphanIntegrityReporter.TRANSFERS_WITHOUT_RECEIVER));
        assertEquals(lastSuccess,
                registry.get("db.orphan.last_success_epoch_seconds").gauge().value());
    }

    private static double gaugeValue(SimpleMeterRegistry registry, String type) {
        return registry.get("db.orphan.current").tag("type", type).gauge().value();
    }

    /**
     * Simulates a replica that lost the advisory lock: the guarded work must
     * not run at all.
     */
    static class LosingReplicaLock extends AdvisorySchedulerLock {

        LosingReplicaLock() {
            super(mock(JdbcTemplate.class), mock(PlatformTransactionManager.class));
        }

        @Override
        public boolean runIfLeader(String lockName, Runnable work) {
            return false;
        }
    }
}
