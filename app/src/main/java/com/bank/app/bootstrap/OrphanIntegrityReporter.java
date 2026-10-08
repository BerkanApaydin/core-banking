package com.bank.app.bootstrap;

import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observes referential integrity after V24 restored and validated database
 * foreign keys. An orphan would now indicate a broken constraint, disabled
 * enforcement, or corrupted/partially restored data. This job only reports;
 * it never repairs or deletes records. The last-success gauge distinguishes
 * an actual clean scan from the initial zero-valued orphan gauges.
 */
@Service
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "app.integrity", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrphanIntegrityReporter {

    private static final Logger log = LoggerFactory.getLogger(OrphanIntegrityReporter.class);

    static final String ACCOUNTS_WITHOUT_USER = "accounts_without_user";
    static final String TRANSFERS_WITHOUT_SENDER = "transfers_without_sender";
    static final String TRANSFERS_WITHOUT_RECEIVER = "transfers_without_receiver";

    private static final String ACCOUNTS_SQL =
            "SELECT COUNT(*) FROM accounts a LEFT JOIN users u ON u.id = a.user_id WHERE u.id IS NULL";
    private static final String SENDERS_SQL =
            "SELECT COUNT(*) FROM transfers t LEFT JOIN accounts s ON s.id = t.sender_account_id WHERE s.id IS NULL";
    private static final String RECEIVERS_SQL =
            "SELECT COUNT(*) FROM transfers t LEFT JOIN accounts r ON r.id = t.receiver_account_id WHERE r.id IS NULL";

    private final JdbcTemplate jdbc;
    private final AdvisorySchedulerLock schedulerLock;
    private final long alarmThreshold;
    private final AtomicLong accountsWithoutUser = new AtomicLong();
    private final AtomicLong transfersWithoutSender = new AtomicLong();
    private final AtomicLong transfersWithoutReceiver = new AtomicLong();
    private final AtomicLong lastSuccessfulScanEpochSeconds = new AtomicLong();
    private final Counter alarmCounter;

    public OrphanIntegrityReporter(JdbcTemplate jdbc,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this(jdbc, meterRegistry, new OrphanIntegrityProperties(true, "0 0 3 * * *", 0),
                AdvisorySchedulerLock.alwaysRun());
    }

    public OrphanIntegrityReporter(JdbcTemplate jdbc,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry,
            OrphanIntegrityProperties properties) {
        this(jdbc, meterRegistry, properties, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public OrphanIntegrityReporter(JdbcTemplate jdbc,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry,
            OrphanIntegrityProperties properties,
            AdvisorySchedulerLock schedulerLock) {
        this.jdbc = jdbc;
        this.schedulerLock = schedulerLock;
        this.alarmThreshold = properties != null ? properties.orphanAlarmThreshold() : 0;
        Counter counter = null;
        if (meterRegistry != null) {
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", ACCOUNTS_WITHOUT_USER)), accountsWithoutUser);
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", TRANSFERS_WITHOUT_SENDER)), transfersWithoutSender);
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", TRANSFERS_WITHOUT_RECEIVER)), transfersWithoutReceiver);
            meterRegistry.gauge("db.orphan.last_success_epoch_seconds", lastSuccessfulScanEpochSeconds);
            // Alarm hook for Alertmanager/PagerDuty, e.g.:
            //   sum(increase(db_orphan_alarm_total[1h])) by (type) > 0
            counter = Counter.builder("db.orphan.alarm")
                    .description("Orphan rows above the alarm threshold (out-of-band deletion suspected)")
                    .register(meterRegistry);
        }
        this.alarmCounter = counter;
    }

    @Scheduled(cron = "${app.integrity.orphan-check-cron:0 0 3 * * *}")
    public void reportOrphans() {
        // K12/D5: single-flight across replicas; only the leader's gauges and
        // alarm counter move, so db_orphan_alarm_total is no longer multiplied
        // by the replica count. Staleness alerts must aggregate with max()
        // across instances (see k8s/prometheus-rules.yaml).
        schedulerLock.runIfLeader("orphan-integrity-scan", () -> {
            check(ACCOUNTS_WITHOUT_USER, ACCOUNTS_SQL, accountsWithoutUser);
            check(TRANSFERS_WITHOUT_SENDER, SENDERS_SQL, transfersWithoutSender);
            check(TRANSFERS_WITHOUT_RECEIVER, RECEIVERS_SQL, transfersWithoutReceiver);
            lastSuccessfulScanEpochSeconds.set(Instant.now().getEpochSecond());
        });
    }

    private void check(String type, String sql, AtomicLong gauge) {
        Long count = jdbc.queryForObject(sql, Long.class);
        long orphans = count == null ? 0 : count;
        gauge.set(orphans);
        if (orphans > alarmThreshold) {
            if (alarmCounter != null) {
                alarmCounter.increment(orphans);
            }
            log.error("ORPHAN ALARM: {} orphan row(s) of type '{}' exceed threshold {}. "
                            + "Constraint enforcement or restore integrity requires manual review; automatic cleanup is disabled.",
                    orphans, type, alarmThreshold);
        } else if (orphans > 0) {
            log.warn("Orphan integrity: {} orphan row(s) of type '{}' detected (below alarm threshold {}). Manual review required; automatic cleanup is disabled by design.",
                    orphans, type, alarmThreshold);
        } else {
            log.debug("Orphan integrity: no orphans of type '{}'.", type);
        }
    }
}
