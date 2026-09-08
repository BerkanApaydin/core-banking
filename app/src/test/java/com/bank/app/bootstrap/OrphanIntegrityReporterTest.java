package com.bank.app.bootstrap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanIntegrityReporterTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private MeterRegistry meterRegistry;

    @BeforeEach
    void stubGauges() {
        lenient().when(meterRegistry.gauge(anyString(), any(Iterable.class), any(AtomicLong.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void shouldRunThreeChecksWhenClean() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldReportWithoutDeletingWhenOrphansExist() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldExposeCountsAsGauges() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var gaugeCaptor = ArgumentCaptor.forClass(AtomicLong.class);

        new OrphanIntegrityReporter(jdbc, meterRegistry).reportOrphans();

        verify(meterRegistry, times(3)).gauge(anyString(), any(Iterable.class), gaugeCaptor.capture());
        assertThatCapturedGaugesAre(gaugeCaptor, 2L);
    }

    @Test
    void shouldTolerateNullCounts() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> null);

        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(jdbc, meterRegistry);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
    }

    @Test
    void shouldStartWithoutMeterRegistry() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        OrphanIntegrityReporter reporter = new OrphanIntegrityReporter(jdbc, null);
        assertDoesNotThrow(reporter::reportOrphans);

        verify(jdbc, times(3)).queryForObject(anyString(), eq(Long.class));
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
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        assertNotNull(findEventAtLevel(Level.ERROR));
    }

    @Test
    void shouldLogWarnWhenOrphansBelowThreshold() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        Logger logger =
                (Logger) LoggerFactory.getLogger(OrphanIntegrityReporter.class);
        ListAppender<ILoggingEvent> appender =
                new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new OrphanIntegrityReporter(jdbc, meterRegistry,
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

        new OrphanIntegrityReporter(jdbc, registry,
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 0)).reportOrphans();

        // 2 orphans x 3 checks, all above threshold 0
        assertEquals(6.0, registry.get("db.orphan.alarm").counter().count());
    }

    @Test
    void shouldNotIncrementAlarmCounterWhenBelowThreshold() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);
        var registry = new SimpleMeterRegistry();

        new OrphanIntegrityReporter(jdbc, registry,
                new OrphanIntegrityProperties(true, "0 0 3 * * *", 5)).reportOrphans();

        assertEquals(0.0, registry.get("db.orphan.alarm").counter().count());
    }

    @Test
    void shouldNotLogWarnWhenClean() {
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
            new OrphanIntegrityReporter(jdbc, meterRegistry).reportOrphans();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(e -> e.getLevel() == level)
                .findFirst()
                .orElse(null);
    }
}
