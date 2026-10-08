package com.bank.app.infrastructure.adapter.in.outbox;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Retention hygiene for the transactional outbox and its handler dedup keys.
 *
 * <p>Deletes acknowledged, non-dead-letter outbox rows older than the retention
 * window, then handler dedup keys ({@code outbox_handler_*}) older than the
 * same cutoff — in that order, so a dedup key never disappears while its event
 * row still exists. Unprocessed rows (redelivery source), dead letters
 * (investigation evidence) and {@code PENDING} keys (stuck reservations stay
 * visible) are never touched. {@code audit_logs} and {@code ledger_entries}
 * are intentionally excluded: they are the legal/audit trail, not a queue.
 */
@Service
public class OutboxRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionScheduler.class);

    static final String LOCK_NAME = "outbox-retention";

    private final OutboxPort outboxPort;
    private final IdempotencyPort idempotencyPort;
    private final OutboxProperties outboxProperties;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;

    public OutboxRetentionScheduler(
            OutboxPort outboxPort,
            IdempotencyPort idempotencyPort,
            OutboxProperties outboxProperties) {
        this(outboxPort, idempotencyPort, outboxProperties, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public OutboxRetentionScheduler(
            OutboxPort outboxPort,
            IdempotencyPort idempotencyPort,
            OutboxProperties outboxProperties,
            AdvisorySchedulerLock schedulerLock) {
        this(outboxPort, idempotencyPort, outboxProperties, schedulerLock, null);
    }

    public OutboxRetentionScheduler(
            OutboxPort outboxPort,
            IdempotencyPort idempotencyPort,
            OutboxProperties outboxProperties,
            AdvisorySchedulerLock schedulerLock,
            ClockProviderPort clockProvider) {
        this.outboxPort = outboxPort;
        this.idempotencyPort = idempotencyPort;
        this.outboxProperties = outboxProperties;
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
    }

    @Scheduled(cron = "${app.outbox.retention-cron:0 0 5 * * 0}")
    public void retainProcessed() {
        // K12/D5: single-flight across replicas. No @Transactional here — the
        // lock guard owns the transaction (same rule as IdempotencyCleanupScheduler).
        schedulerLock.runIfLeader(LOCK_NAME, () -> {
            Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(outboxProperties.retentionDays());
            int outboxDeleted = outboxPort.deleteProcessedBefore(cutoff);
            int handlerDeleted = idempotencyPort.deleteExpiredHandlerKeys(cutoff);
            log.info("Outbox retention completed: cutoff={}, processedRowsDeleted={}, handlerKeysDeleted={}",
                    cutoff, outboxDeleted, handlerDeleted);
        });
    }
}
