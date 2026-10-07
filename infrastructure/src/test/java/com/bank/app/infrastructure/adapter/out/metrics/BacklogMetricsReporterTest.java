package com.bank.app.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;

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
        reporter = new BacklogMetricsReporter(jdbc, registry);
    }

    @Test
    void publishesBothSnapshotsAndZeroForEmptyBacklogs() {
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(2, LocalDateTime.now().minusMinutes(5)),
                        new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(1, LocalDateTime.now().minusMinutes(2)),
                        new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.LEDGER_NONZERO_SQL), eq(Long.class)))
                .thenReturn(0L, 0L);

        reporter.scan();

        assertEquals(2, gauge("outbox.pending.current"));
        assertTrue(gauge("outbox.oldest_pending.age_seconds") >= 299);
        assertEquals(1, gauge("idempotency.http.pending.current"));
        assertTrue(gauge("idempotency.http.oldest_pending.age_seconds") >= 119);
        assertEquals(0, gauge("ledger.nonzero_transaction_refs"));
        assertTrue(gauge("backlog.last_success_epoch_seconds") > 0);

        reporter.scan();

        assertEquals(0, gauge("outbox.pending.current"));
        assertEquals(0, gauge("outbox.oldest_pending.age_seconds"));
        assertEquals(0, gauge("idempotency.http.pending.current"));
        assertEquals(0, gauge("idempotency.http.oldest_pending.age_seconds"));
        assertEquals(0, gauge("ledger.nonzero_transaction_refs"));
    }

    @Test
    void publishesNonzeroLedgerGroupsForAlerting() {
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.OUTBOX_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.HTTP_PENDING_SQL), any(RowMapper.class)))
                .thenReturn(new BacklogMetricsReporter.Backlog(0, null));
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.LEDGER_NONZERO_SQL), eq(Long.class)))
                .thenReturn(3L);

        reporter.scan();

        assertEquals(3, gauge("ledger.nonzero_transaction_refs"));
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
        when(jdbc.queryForObject(eq(BacklogMetricsReporter.LEDGER_NONZERO_SQL), eq(Long.class)))
                .thenReturn(0L);

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
