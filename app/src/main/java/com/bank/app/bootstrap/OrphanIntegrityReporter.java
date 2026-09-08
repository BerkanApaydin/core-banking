package com.bank.app.bootstrap;

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

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compensating observer for the intentional no-FK decision (see
 * {@code V19__document_no_fk_decision.sql}): aggregates reference each other
 * by ID only, so orphaned rows (e.g. an account whose user was removed
 * out-of-band) cannot be prevented by the database. This job only
 * <em>detects and reports</em> — it never deletes. Cleanup stays an explicit,
 * reviewed manual operation, never {@code ON DELETE CASCADE}.
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
    private final long alarmThreshold;
    private final AtomicLong accountsWithoutUser = new AtomicLong();
    private final AtomicLong transfersWithoutSender = new AtomicLong();
    private final AtomicLong transfersWithoutReceiver = new AtomicLong();
    private final Counter alarmCounter;

    public OrphanIntegrityReporter(JdbcTemplate jdbc,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this(jdbc, meterRegistry, new OrphanIntegrityProperties(true, "0 0 3 * * *", 0));
    }

    @Autowired
    public OrphanIntegrityReporter(JdbcTemplate jdbc,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry,
            OrphanIntegrityProperties properties) {
        this.jdbc = jdbc;
        this.alarmThreshold = properties != null ? properties.orphanAlarmThreshold() : 0;
        Counter counter = null;
        if (meterRegistry != null) {
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", ACCOUNTS_WITHOUT_USER)), accountsWithoutUser);
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", TRANSFERS_WITHOUT_SENDER)), transfersWithoutSender);
            meterRegistry.gauge("db.orphan.current", List.of(Tag.of("type", TRANSFERS_WITHOUT_RECEIVER)), transfersWithoutReceiver);
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
        check(ACCOUNTS_WITHOUT_USER, ACCOUNTS_SQL, accountsWithoutUser);
        check(TRANSFERS_WITHOUT_SENDER, SENDERS_SQL, transfersWithoutSender);
        check(TRANSFERS_WITHOUT_RECEIVER, RECEIVERS_SQL, transfersWithoutReceiver);
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
                            + "Out-of-band deletion suspected — manual review required; automatic cleanup is disabled by design.",
                    orphans, type, alarmThreshold);
        } else if (orphans > 0) {
            log.warn("Orphan integrity: {} orphan row(s) of type '{}' detected (below alarm threshold {}). Manual review required; automatic cleanup is disabled by design.",
                    orphans, type, alarmThreshold);
        } else {
            log.debug("Orphan integrity: no orphans of type '{}'.", type);
        }
    }
}
