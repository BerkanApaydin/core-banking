package com.bank.app.transfer.adapter.in.scheduler;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.config.TransferReaperProperties;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.exception.TransferNotPendingException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crash-window reaper for the synchronous placement path.
 *
 * <p>Background: {@code PlaceTransferUseCaseImpl} persists the PENDING row
 * first (the ID is needed for the COMPLETED event) and rolls everything back
 * on failure — except a JVM crash between the two saves, which leaves a
 * PENDING row whose money movement never happened. This job transitions such
 * leftovers to FAILED through the domain ({@code Transfer.markFailed}, the
 * production path that was previously async-only), oldest first, in bounded
 * batches.
 *
 * <p>Concurrency: no distributed lock. Each row is re-locked
 * ({@code findByIdForUpdate}) and written through the versioned bulk UPDATE,
 * so a racing completion/cancellation — or another replica's reaper — shows
 * up as {@code TransferNotPendingException} / optimistic-lock failure. Those
 * are counted, not retried: the other writer won legitimately.
 *
 * <p>No domain-event publish: there is no outbox relay for FAILED yet, and an
 * unhandled event type would poison the outbox (see
 * {@code docs/decisions/transfer-failed-state.md}). The audit row below is
 * the trail until a FAILED consumer lands.
 */
@Component
@ConditionalOnProperty(prefix = "app.transfer.reaper", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransferPendingReaper {

    private static final Logger log = LoggerFactory.getLogger(TransferPendingReaper.class);

    static final String REAPED_COUNTER = "transfer.pending.reaped";
    static final String CONFLICT_COUNTER = "transfer.pending.reap-conflicts";

    private final LoadTransferPort loadTransferPort;
    private final SaveTransferPort saveTransferPort;
    private final AuditEventPort auditEventPort;
    private final TransferReaperProperties properties;
    private final ClockProviderPort clockProvider;
    private final MeterRegistry meterRegistry;

    public TransferPendingReaper(LoadTransferPort loadTransferPort,
            SaveTransferPort saveTransferPort,
            AuditEventPort auditEventPort,
            TransferReaperProperties properties,
            ClockProviderPort clockProvider) {
        this(loadTransferPort, saveTransferPort, auditEventPort, properties, clockProvider, null);
    }

    @Autowired
    public TransferPendingReaper(LoadTransferPort loadTransferPort,
            SaveTransferPort saveTransferPort,
            AuditEventPort auditEventPort,
            TransferReaperProperties properties,
            ClockProviderPort clockProvider,
            @Autowired(required = false) @Nullable MeterRegistry meterRegistry) {
        this.loadTransferPort = loadTransferPort;
        this.saveTransferPort = saveTransferPort;
        this.auditEventPort = auditEventPort;
        this.properties = properties;
        this.clockProvider = clockProvider;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "${app.transfer.reaper.cron:0 */5 * * * *}")
    public void reap() {
        Clock clock = clockProvider.clock();
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(properties.olderThan());
        List<Transfer> stale;
        try {
            stale = loadTransferPort.findStalePending(cutoff, properties.batchSize());
        } catch (RuntimeException e) {
            // A missed schedule only delays detection; the next run retries.
            // Never let a store failure kill the scheduler thread.
            log.warn("Pending-transfer reap scan failed: {}", e.getClass().getSimpleName());
            return;
        }
        if (stale.isEmpty()) {
            log.debug("Pending-transfer reap: no stale rows older than {}", cutoff);
            return;
        }
        log.info("Pending-transfer reap: {} stale row(s) older than {}", stale.size(), cutoff);
        for (Transfer transfer : stale) {
            reapOne(transfer, clock);
        }
    }

    private void reapOne(Transfer stale, Clock clock) {
        Long id = stale.getId();
        try {
            Transfer locked = loadTransferPort.findByIdForUpdate(id).orElse(null);
            if (locked == null) {
                return;
            }
            locked.markFailed(clock);
            saveTransferPort.save(locked);
            auditEventPort.publish(new AuditEvent("TRANSFER_MARKED_FAILED",
                    "Stale PENDING transfer marked FAILED by reaper. Transfer ID: " + id,
                    LocalDateTime.now(clock), "system"));
            count(REAPED_COUNTER);
            log.info("Pending-transfer reaped: id={}", id);
        } catch (TransferNotPendingException | OptimisticLockingFailureException e) {
            // A concurrent completion/cancellation/reaper won the row: the
            // transfer is no longer PENDING, which is the desired end state.
            count(CONFLICT_COUNTER);
            log.debug("Pending-transfer reap conflict: id={}, reason={}", id, e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // Per-row isolation: one poisoned row must not starve the rest of
            // the batch. The next schedule retries it.
            log.warn("Pending-transfer reap failed: id={}, failureType={}", id, e.getClass().getName());
        }
    }

    private void count(String name) {
        if (meterRegistry != null) {
            meterRegistry.counter(name).increment();
        }
    }
}
