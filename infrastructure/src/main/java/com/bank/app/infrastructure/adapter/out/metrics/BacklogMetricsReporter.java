package com.bank.app.infrastructure.adapter.out.metrics;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

/** Periodic, read-only backlog snapshot; gauges are never updated from a partial scan. */
@Component
@ConditionalOnProperty(prefix = "app.observability.backlog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BacklogMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(BacklogMetricsReporter.class);

    static final String OUTBOX_SQL = "SELECT count(*), min(created_at) FROM outbox_events "
            + "WHERE processed = false AND dead_letter = false";
    static final String HTTP_PENDING_SQL = "SELECT count(*), min(created_at) FROM idempotency_keys "
            + "WHERE status = 'PENDING' AND key_kind = 'HTTP'";
    /**
     * Money-movement invariant (authoritative check lives in
     * {@code LedgerReconciliationJob}, nightly — including the
     * {@code ledger.nonzero_transaction_refs} gauge): the two legs sharing a
     * transaction_ref must net to zero. PERF-1: this 60s reporter MUST NOT run
     * the full-table GROUP BY — ledger_entries is append-only and unbounded,
     * so a minutely heap scan + hash aggregate degrades linearly.
     */
    static final String LEDGER_NONZERO_SQL = "SELECT count(*) FROM (SELECT transaction_ref FROM ledger_entries "
            + "GROUP BY transaction_ref HAVING SUM(CASE WHEN direction = 'CREDIT' "
            + "THEN amount ELSE -amount END) <> 0) t";
    /**
     * Partition-drift guard (partitioning.md step 5): rows landing in the
     * DEFAULT partition mean timestamps fell outside every pre-created range,
     * i.e. ensure_audit_partition.sql was missed. Pre-cutover the table does
     * not exist and the gauge reads 0: the existence check below runs first
     * because PostgreSQL resolves every table reference at plan time, so a
     * CASE-guarded single query would still fail with "relation does not
     * exist" on the untaken branch.
     */
    static final String TABLE_EXISTS_SQL =
            "SELECT count(*) FROM pg_class WHERE relname = ? "
            + "AND relnamespace = 'public'::regnamespace AND relkind IN ('r', 'p', 'm', 'f')";
    static final String AUDIT_DEFAULT_PARTITION_COUNT_SQL = "SELECT count(*) FROM audit_logs_default";

    private final JdbcTemplate jdbc;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;
    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxOldestAgeSeconds = new AtomicLong();
    private final AtomicLong httpPending = new AtomicLong();
    private final AtomicLong httpOldestAgeSeconds = new AtomicLong();
    private final AtomicLong auditDefaultPartitionRows = new AtomicLong();
    private final AtomicLong lastSuccessfulScanEpochSeconds = new AtomicLong();

    public BacklogMetricsReporter(JdbcTemplate jdbc, MeterRegistry meterRegistry) {
        this(jdbc, meterRegistry, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public BacklogMetricsReporter(JdbcTemplate jdbc, MeterRegistry meterRegistry,
                                  AdvisorySchedulerLock schedulerLock) {
        this(jdbc, meterRegistry, schedulerLock, null);
    }

    public BacklogMetricsReporter(JdbcTemplate jdbc, MeterRegistry meterRegistry,
                                  AdvisorySchedulerLock schedulerLock,
                                  ClockProviderPort clockProvider) {
        this.jdbc = jdbc;
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
        meterRegistry.gauge("outbox.pending.current", outboxPending);
        meterRegistry.gauge("outbox.oldest_pending.age_seconds", outboxOldestAgeSeconds);
        meterRegistry.gauge("idempotency.http.pending.current", httpPending);
        meterRegistry.gauge("idempotency.http.oldest_pending.age_seconds", httpOldestAgeSeconds);
        meterRegistry.gauge("audit.default_partition.rows", auditDefaultPartitionRows);
        meterRegistry.gauge("backlog.last_success_epoch_seconds", lastSuccessfulScanEpochSeconds);
    }

    @Scheduled(fixedDelayString = "${app.observability.backlog.scan-delay-ms:60000}")
    public void scan() {
        // K12/D5: single-flight across replicas; only the leader's gauges move.
        // Staleness alerts must aggregate with max() across instances (see
        // k8s/prometheus-rules.yaml) so followers' frozen gauges never page.
        schedulerLock.runIfLeader("backlog-metrics-scan", () -> {
        try {
            Backlog outbox = read(OUTBOX_SQL);
            Backlog http = read(HTTP_PENDING_SQL);
            // PERF-1: ledger invariant intentionally NOT scanned here (see
            // LEDGER_NONZERO_SQL javadoc). LedgerReconciliationJob owns it.
            long defaultPartitionRows = tableExists("audit_logs_default")
                    ? readCount(AUDIT_DEFAULT_PARTITION_COUNT_SQL)
                    : 0;
            Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
            LocalDateTime now = LocalDateTime.now(clock);
            long completedAt = Instant.now(clock).getEpochSecond();
            outboxPending.set(outbox.count());
            outboxOldestAgeSeconds.set(ageSeconds(outbox, now));
            httpPending.set(http.count());
            httpOldestAgeSeconds.set(ageSeconds(http, now));
            auditDefaultPartitionRows.set(defaultPartitionRows);
            lastSuccessfulScanEpochSeconds.set(completedAt);
        } catch (RuntimeException e) {
            // Keep the preceding snapshot and its timestamp. A zero-valued gauge
            // with a stale last-success time must not be mistaken for no backlog.
            log.warn("Backlog metrics scan failed: failureType={}", e.getClass().getName());
        }
        });
    }

    private Backlog read(String sql) {
        // V46 stores these columns as TIMESTAMPTZ, which pgjdbc refuses to
        // hand over as LocalDateTime directly ("Cannot convert the column of
        // type TIMESTAMPTZ to requested type java.time.LocalDateTime").
        // Read the instant type (OffsetDateTime) and drop to the UTC wall
        // clock the rest of the codebase reasons in.
        Backlog result = jdbc.queryForObject(sql, (rs, rowNum) -> {
            OffsetDateTime oldest = rs.getObject(2, OffsetDateTime.class);
            return new Backlog(rs.getLong(1), oldest == null ? null : oldest.toLocalDateTime());
        });
        if (result == null) {
            throw new IllegalStateException("Backlog aggregate query returned no row");
        }
        return result;
    }

    private long readCount(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        if (result == null) {
            throw new IllegalStateException("Ledger reconcile query returned no row");
        }
        return result;
    }

    private boolean tableExists(String table) {
        Integer result = jdbc.queryForObject(TABLE_EXISTS_SQL, Integer.class, table);
        return result != null && result > 0;
    }

    private static long ageSeconds(Backlog backlog, LocalDateTime now) {
        return backlog.oldest() == null ? 0 : Math.max(0, Duration.between(backlog.oldest(), now).getSeconds());
    }

    record Backlog(long count, LocalDateTime oldest) {}
}
