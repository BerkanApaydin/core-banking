package com.bank.app.infrastructure.adapter.in.audit;

import com.bank.app.audit.application.port.out.AuditRetentionPort;
import com.bank.app.audit.config.AuditProperties;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Retention hygiene for the legal audit trail. Deletes audit rows older than
 * {@code app.audit.retention-days} (default 365 — an order of magnitude longer
 * than the outbox 30-day window, reflecting its evidentiary role). Runs
 * weekly by default, single-flight across replicas via the advisory lock.
 * Deletion is batched (1000 rows x at most 10 batches per run): a larger
 * backlog drains over consecutive schedules instead of exhausting the 30s
 * scheduler-lock budget in one transaction.
 */
@Service
@ConditionalOnProperty(name = "app.audit.retention-enabled", havingValue = "true", matchIfMissing = true)
public class AuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionScheduler.class);

    static final String LOCK_NAME = "audit-retention";

    private final AuditRetentionPort retentionPort;
    private final AuditProperties auditProperties;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;

    // Single canonical constructor for Spring. Tests use the named forTests
    // factories instead of ambiguous overloads.
    @Autowired
    public AuditRetentionScheduler(
            AuditRetentionPort retentionPort,
            AuditProperties auditProperties,
            AdvisorySchedulerLock schedulerLock) {
        this(retentionPort, auditProperties, schedulerLock, null);
    }

    public AuditRetentionScheduler(
            AuditRetentionPort retentionPort,
            AuditProperties auditProperties,
            AdvisorySchedulerLock schedulerLock,
            ClockProviderPort clockProvider) {
        this.retentionPort = retentionPort;
        this.auditProperties = auditProperties;
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
    }

    static AuditRetentionScheduler forTests(
            AuditRetentionPort retentionPort,
            AuditProperties auditProperties,
            AdvisorySchedulerLock schedulerLock) {
        return new AuditRetentionScheduler(retentionPort, auditProperties, schedulerLock, null);
    }

    @Scheduled(cron = "${app.audit.retention-cron:0 0 4 * * 0}")
    public void retainHistory() {
        schedulerLock.runIfLeader(LOCK_NAME, () -> {
            Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(auditProperties.retentionDays());
            int deleted = retentionPort.deleteOlderThan(cutoff);
            log.info("Audit retention completed: cutoff={}, rowsDeleted={}", cutoff, deleted);
        });
    }
}
