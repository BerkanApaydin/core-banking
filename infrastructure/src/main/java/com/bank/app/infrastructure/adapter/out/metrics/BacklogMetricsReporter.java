package com.bank.app.infrastructure.adapter.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** Periodic, read-only backlog snapshot; gauges are never updated from a partial scan. */
@Component
@ConditionalOnProperty(prefix = "app.observability.backlog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BacklogMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(BacklogMetricsReporter.class);

    static final String OUTBOX_SQL = "SELECT count(*), min(created_at) FROM outbox_events "
            + "WHERE processed = false AND dead_letter = false";
    static final String HTTP_PENDING_SQL = "SELECT count(*), min(created_at) FROM idempotency_keys "
            + "WHERE status = 'PENDING' AND left(key_value, 5) = 'http_'";

    private final JdbcTemplate jdbc;
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxOldestAgeSeconds = new AtomicLong();
    private final AtomicLong httpPending = new AtomicLong();
    private final AtomicLong httpOldestAgeSeconds = new AtomicLong();
    private final AtomicLong lastSuccessfulScanEpochSeconds = new AtomicLong();

    public BacklogMetricsReporter(JdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        meterRegistry.gauge("outbox.pending.current", outboxPending);
        meterRegistry.gauge("outbox.oldest_pending.age_seconds", outboxOldestAgeSeconds);
        meterRegistry.gauge("idempotency.http.pending.current", httpPending);
        meterRegistry.gauge("idempotency.http.oldest_pending.age_seconds", httpOldestAgeSeconds);
        meterRegistry.gauge("backlog.last_success_epoch_seconds", lastSuccessfulScanEpochSeconds);
    }

    @Scheduled(fixedDelayString = "${app.observability.backlog.scan-delay-ms:60000}")
    public void scan() {
        try {
            Backlog outbox = read(OUTBOX_SQL);
            Backlog http = read(HTTP_PENDING_SQL);
            LocalDateTime now = LocalDateTime.now();
            long completedAt = Instant.now().getEpochSecond();
            outboxPending.set(outbox.count());
            outboxOldestAgeSeconds.set(ageSeconds(outbox, now));
            httpPending.set(http.count());
            httpOldestAgeSeconds.set(ageSeconds(http, now));
            lastSuccessfulScanEpochSeconds.set(completedAt);
        } catch (RuntimeException e) {
            // Keep the preceding snapshot and its timestamp. A zero-valued gauge
            // with a stale last-success time must not be mistaken for no backlog.
            log.warn("Backlog metrics scan failed: failureType={}", e.getClass().getName());
        }
    }

    private Backlog read(String sql) {
        Backlog result = jdbc.queryForObject(sql, (rs, rowNum) ->
                new Backlog(rs.getLong(1), rs.getObject(2, LocalDateTime.class)));
        if (result == null) {
            throw new IllegalStateException("Backlog aggregate query returned no row");
        }
        return result;
    }

    private static long ageSeconds(Backlog backlog, LocalDateTime now) {
        return backlog.oldest() == null ? 0 : Math.max(0, Duration.between(backlog.oldest(), now).getSeconds());
    }

    record Backlog(long count, LocalDateTime oldest) {}
}
