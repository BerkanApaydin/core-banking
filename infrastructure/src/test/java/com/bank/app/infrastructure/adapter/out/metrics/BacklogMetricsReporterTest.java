package com.bank.app.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class BacklogMetricsReporterTest {

    @Mock
    private JdbcTemplate jdbc;

    private SimpleMeterRegistry registry;
    private BacklogMetricsReporter reporter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        // UTC clock matches production ClockProviderPort; row timestamps below
        // are UTC-based so age math is zone-consistent (E-2/Short-10).
        Clock fixed =
                Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        reporter = new BacklogMetricsReporter(jdbc, registry,
                AdvisorySchedulerLock.alwaysRun(),
                () -> fixed);
    }

    @Test
    void publishesBothSnapshotsAndZeroForEmptyBacklogs() {
        LocalDateTime utcNow = LocalDateTime.of(2026, 5, 1, 12, 0);
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(2, utcNow.minusMinutes(5)),
                        new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(1, utcNow.minusMinutes(2)),
                        new BacklogMetricsReporter.Backlog(0, null));
        // PERF-1: the minutely scan no longer touches ledger_entries; the
        // ledger gauge keeps its initial 0 until the nightly job updates it.
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.TABLE_EXISTS_SQL), eq(Integer.class),
                eq("audit_logs_default")))
                .thenReturn(0, 0);

        reporter.scan();

        assertEquals(2, gauge("outbox.pending.current"));
        assertTrue(gauge("outbox.oldest_pending.age_seconds") >= 299);
        assertEquals(1, gauge("idempotency.http.pending.current"));
        assertTrue(gauge("idempotency.http.oldest_pending.age_seconds") >= 119);
        assertEquals(0, gauge("audit.default_partition.rows"));
        assertTrue(gauge("backlog.last_success_epoch_seconds") > 0);

        reporter.scan();

        assertEquals(0, gauge("outbox.pending.current"));
        assertEquals(0, gauge("outbox.oldest_pending.age_seconds"));
        assertEquals(0, gauge("idempotency.http.pending.current"));
        assertEquals(0, gauge("idempotency.http.oldest_pending.age_seconds"));
    }

    @Test
    void keepsLedgerGaugeUntouchedByMinutelyScan() {
        // PERF-1 regression: the 60s scan must not query ledger_entries at all,
        // and must not register the gauge either (double registration with the
        // nightly LedgerReconciliationJob would leave the job's updates wired
        // to a dead meter). The gauge lives only in the job.
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.TABLE_EXISTS_SQL), eq(Integer.class),
                eq("audit_logs_default")))
                .thenReturn(0);

        reporter.scan();

        assertTrue(registry.find("ledger.nonzero_transaction_refs").gauge() == null,
                "minutely reporter must not register the ledger gauge");
        assertEquals(0, gauge("audit.default_partition.rows"));
        assertTrue(gauge("backlog.last_success_epoch_seconds") > 0);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
                .queryForObject(eq(BacklogMetricsReporter.LEDGER_NONZERO_SQL), eq(Long.class));
    }

    @Test
    void publishesDefaultPartitionDriftForAlerting() {
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.TABLE_EXISTS_SQL), eq(Integer.class),
                eq("audit_logs_default")))
                .thenReturn(1);
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.AUDIT_DEFAULT_PARTITION_COUNT_SQL), eq(Long.class)))
                .thenReturn(7L);

        reporter.scan();

        assertEquals(7, gauge("audit.default_partition.rows"));
        assertTrue(gauge("backlog.last_success_epoch_seconds") > 0);
    }

    @Test
    void keepsPreviousSnapshotWhenEitherQueryFails() {
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(2, LocalDateTime.now().minusMinutes(5)),
                        new BacklogMetricsReporter.Backlog(99, LocalDateTime.now().minusDays(1)));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(1, LocalDateTime.now().minusMinutes(2)))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.TABLE_EXISTS_SQL), eq(Integer.class),
                eq("audit_logs_default")))
                .thenReturn(0);

        reporter.scan();
        double previousSuccess = gauge("backlog.last_success_epoch_seconds");
        reporter.scan();

        assertEquals(2, gauge("outbox.pending.current"));
        assertEquals(1, gauge("idempotency.http.pending.current"));
        assertEquals(previousSuccess, gauge("backlog.last_success_epoch_seconds"));
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
