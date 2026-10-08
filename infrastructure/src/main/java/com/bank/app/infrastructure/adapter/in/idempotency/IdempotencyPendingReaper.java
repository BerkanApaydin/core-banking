package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.IdempotencyReaperProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Crash-window reaper for HTTP idempotency reservations.
 *
 * <p>Background: {@code IdempotencyGuard.startRequest} commits the PENDING
 * claim in its own REQUIRES_NEW transaction before the business work runs.
 * A JVM crash between the claim commit and the outcome (COMPLETED/FAILED)
 * leaves a PENDING row whose owner will never finish it. The row is excluded
 * from retention cleanup by design (only terminal rows are deleted), so
 * without this job the same key retries PENDING forever (409 CONTENDED)
 * even though no work is in flight.
 *
 * <p>Recovery transitions stale HTTP PENDING rows (older than
 * {@code app.idempotency.reaper.older-than}) to FAILED in one bulk UPDATE
 * (backed by {@code idx_idempotency_pending_http_kind}, V38). FAILED is
 * retryable: the next attempt with the same key goes through
 * {@code tryResetFailed} and re-executes. A different payload with the same
 * key is still rejected with a payload-conflict error, so no confused retry
 * can hijack another request's key.
 *
 * <p>Concurrency: single-flight across replicas via
 * {@code AdvisorySchedulerLock}. The bulk UPDATE is idempotent, so a lost
 * lock race only duplicates cheap work. A failed run only delays recovery;
 * the next schedule retries. Never throws out of the scheduled method.
 */
@Service
@ConditionalOnProperty(prefix = "app.idempotency.reaper", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyPendingReaper {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPendingReaper.class);

    static final String LOCK_NAME = "idempotency-pending-reaper";
    static final String REAPED_COUNTER = "idempotency.pending.reaped";

    private final IdempotencyPort idempotencyPort;
    private final IdempotencyReaperProperties properties;
    private final AdvisorySchedulerLock schedulerLock;
    private final ClockProviderPort clockProvider;
    private final MeterRegistry meterRegistry;

    public IdempotencyPendingReaper(
            IdempotencyPort idempotencyPort,
            IdempotencyReaperProperties properties) {
        this(idempotencyPort, properties, AdvisorySchedulerLock.alwaysRun());
    }

    @Autowired
    public IdempotencyPendingReaper(
            IdempotencyPort idempotencyPort,
            IdempotencyReaperProperties properties,
            AdvisorySchedulerLock schedulerLock) {
        this(idempotencyPort, properties, schedulerLock, null, null);
    }

    public IdempotencyPendingReaper(
            IdempotencyPort idempotencyPort,
            IdempotencyReaperProperties properties,
            AdvisorySchedulerLock schedulerLock,
            ClockProviderPort clockProvider,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.idempotencyPort = idempotencyPort;
        this.properties = properties;
        this.schedulerLock = schedulerLock;
        this.clockProvider = clockProvider;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${app.idempotency.reaper.cron:0 */5 * * * *}")
    public void reapStalePending() {
        // K12/D5: single-flight across replicas. No @Transactional here — the
        // lock guard owns the transaction (a contended lock marks rollback-only,
        // which must not poison an outer transaction).
        schedulerLock.runIfLeader(LOCK_NAME, () -> {
            try {
                Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
                LocalDateTime threshold = LocalDateTime.now(clock).minus(properties.olderThan());
                int reaped = idempotencyPort.failStalePending(threshold);
                if (reaped > 0) {
                    count(REAPED_COUNTER, reaped);
                    log.info("Reaped {} stale idempotency PENDING key(s) older than {}", reaped, threshold);
                } else {
                    log.debug("Idempotency pending reap: no stale rows older than {}", threshold);
                }
            } catch (RuntimeException e) {
                // A missed schedule only delays recovery; the next run retries.
                // Never let a store failure kill the scheduler thread.
                log.warn("Idempotency pending reap failed: failureType={}", e.getClass().getName());
            }
        });
    }

    private void count(String name, int amount) {
        if (meterRegistry != null) {
            meterRegistry.counter(name).increment(amount);
        }
    }
}
