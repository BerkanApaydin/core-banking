package com.bank.app.infrastructure.adapter.out.metrics;

import com.bank.app.common.AbstractIntegrationTest;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklogMetricsIntegrationTest extends AbstractIntegrationTest {

    private final JdbcTemplate jdbc;

    @Autowired
    BacklogMetricsIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void scansOnlyActiveOutboxEventsAndPendingHttpRequestsOnMigratedPostgres() {
        // UTC clock matches production ClockProviderPort; fixture timestamps are
        // UTC-based so age math is zone-consistent (same fix as the unit test).
        Clock fixed = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BacklogMetricsReporter reporter = new BacklogMetricsReporter(jdbc, registry,
                AdvisorySchedulerLock.alwaysRun(), () -> fixed);
        reporter.scan();
        double priorOutbox = gauge(registry, "outbox.pending.current");
        double priorHttp = gauge(registry, "idempotency.http.pending.current");
        LocalDateTime old = LocalDateTime.now(fixed).minusHours(2);
        String suffix = UUID.randomUUID().toString();

        insertOutbox(UUID.randomUUID().toString(), old, false, false);
        insertOutbox(UUID.randomUUID().toString(), old, true, false);
        insertOutbox(UUID.randomUUID().toString(), old, false, true);
        insertKey("http_" + suffix, "PENDING", old);
        insertKey("outbox_handler_" + suffix, "PENDING", old);
        insertKey("http_completed_" + suffix, "COMPLETED", old);

        reporter.scan();

        assertEquals(priorOutbox + 1, gauge(registry, "outbox.pending.current"));
        assertEquals(priorHttp + 1, gauge(registry, "idempotency.http.pending.current"));
        assertTrue(gauge(registry, "outbox.oldest_pending.age_seconds") >= 7199);
        assertTrue(gauge(registry, "idempotency.http.oldest_pending.age_seconds") >= 7199);
        assertTrue(gauge(registry, "backlog.last_success_epoch_seconds") > 0);
        // Pre-cutover the default partition does not exist: the drift gauge
        // must read 0, not fail the scan (see AUDIT_DEFAULT_PARTITION_SQL).
        assertEquals(0, gauge(registry, "audit.default_partition.rows"));
        // F-DB-1/V51: the V27 string-prefix partial
        // (idx_idempotency_pending_http_created_at) was superseded by the V38
        // kind-based partial and dropped — it must NOT exist, while the V38
        // replacement must.
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'idempotency_keys' "
                + "AND indexname = 'idx_idempotency_pending_http_created_at'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'idempotency_keys' "
                + "AND indexname = 'idx_idempotency_pending_http_kind'", Integer.class));
        // L2/V52: subsumed/dead indexes must stay dropped (write amplification).
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE indexname IN ('idx_outbox_events_unprocessed', 'idx_transfers_created_at', "
                + "'idx_audit_logs_timestamp', 'idx_ledger_account_business', "
                + "'idx_accounts_user_id_status', 'idx_audit_logs_username', "
                + "'idx_audit_logs_username_action', 'idx_idempotency_keys_created_at')",
                Integer.class));
        // L3/V52: retention expression index over COALESCE(processed_at, created_at).
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'outbox_events' "
                + "AND indexname = 'idx_outbox_retention_processed_created'", Integer.class));
        // L6/V53: replay body is unbounded TEXT, never a business-tx failure mode.
        assertEquals("text", jdbc.queryForObject("SELECT udt_name FROM information_schema.columns "
                + "WHERE table_name = 'idempotency_keys' AND column_name = 'response_body'", String.class));
    }

    private void insertOutbox(String id, LocalDateTime createdAt, boolean processed, boolean deadLetter) {
        jdbc.update("INSERT INTO outbox_events "
                + "(id, aggregate_type, aggregate_id, event_type, payload, created_at, processed, dead_letter) "
                + "VALUES (?, 'Test', '1', 'TestEvent', '{}', ?, ?, ?)",
                id, createdAt, processed, deadLetter);
    }

    private void insertKey(String key, String status, LocalDateTime createdAt) {
        String kind = key.startsWith("outbox_handler_") ? "HANDLER" : "HTTP";
        jdbc.update("INSERT INTO idempotency_keys (key_value, status, created_at, key_kind) VALUES (?, ?, ?, ?)",
                key, status, createdAt, kind);
    }

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }
}
