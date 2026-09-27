package com.bank.app.infrastructure.adapter.out.metrics;

import com.bank.app.common.AbstractIntegrationTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacklogMetricsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void scansOnlyActiveOutboxEventsAndPendingHttpRequestsOnMigratedPostgres() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BacklogMetricsReporter reporter = new BacklogMetricsReporter(jdbc, registry);
        reporter.scan();
        double priorOutbox = gauge(registry, "outbox.pending.current");
        double priorHttp = gauge(registry, "idempotency.http.pending.current");
        LocalDateTime old = LocalDateTime.now().minusHours(2);
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
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_indexes "
                + "WHERE tablename = 'idempotency_keys' "
                + "AND indexname = 'idx_idempotency_pending_http_created_at'", Integer.class));
    }

    private void insertOutbox(String id, LocalDateTime createdAt, boolean processed, boolean deadLetter) {
        jdbc.update("INSERT INTO outbox_events "
                + "(id, aggregate_type, aggregate_id, event_type, payload, created_at, processed, dead_letter) "
                + "VALUES (?, 'Test', '1', 'TestEvent', '{}', ?, ?, ?)",
                id, createdAt, processed, deadLetter);
    }

    private void insertKey(String key, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO idempotency_keys (key_value, status, created_at) VALUES (?, ?, ?)",
                key, status, createdAt);
    }

    private static double gauge(SimpleMeterRegistry registry, String name) {
        return registry.get(name).gauge().value();
    }
}
