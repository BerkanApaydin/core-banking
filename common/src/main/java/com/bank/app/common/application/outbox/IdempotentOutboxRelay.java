package com.bank.app.common.application.outbox;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.OutboxEventPort;
import com.bank.app.common.application.port.out.OutboxPort.EventEntry;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Shared idempotent outbox-relay skeleton: persistent dedup guard plus
 * uniform failure wrapping, without any logging or framework dependency.
 *
 * <p>Logging stays in the concrete relays (per-context log lines are asserted
 * by relay tests and differ per bounded context): duplicates route to
 * {@link #onDuplicateSkipped} and delivery failures to
 * {@link #onDeliveryFailed}, whose defaults silently skip and wrap with the
 * concrete handler name. Subclasses override the hooks only to add their own
 * log lines.
 *
 * <p>Dedup-key prefixes are part of the persisted contract: they must never
 * change, or already-processed events reprocess after a deploy. The prefix
 * is also no longer what classifies the key: the reservation is made
 * through {@code tryCreateHandler}, which records the {@code HANDLER} kind
 * explicitly instead of deriving it from the key text (see
 * {@code IdempotencyPort}).
 */
public abstract class IdempotentOutboxRelay implements OutboxEventPort {

    private final IdempotencyPort idempotencyPort;
    private final ClockProviderPort clockProvider;
    private final String dedupKeyPrefix;

    protected IdempotentOutboxRelay(IdempotencyPort idempotencyPort,
                                    ClockProviderPort clockProvider,
                                    String dedupKeyPrefix) {
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "IdempotencyPort must not be null");
        this.clockProvider = clockProvider;
        this.dedupKeyPrefix = Objects.requireNonNull(dedupKeyPrefix, "dedupKeyPrefix must not be null");
    }

    @Override
    public final void handle(EventEntry event) {
        Clock clock = clockProvider != null ? clockProvider.clock() : Clock.systemUTC();
        String dedupKey = dedupKeyPrefix + event.id();
        if (!idempotencyPort.tryCreateHandler(dedupKey, LocalDateTime.now(clock))) {
            // The key already exists: a previous attempt claimed it. A FAILED
            // claim is reopened exactly once per retry (tryResetFailed is
            // conditional on FAILED, so racing workers cannot double-deliver);
            // any other state is a genuine duplicate and is skipped.
            if (!reopenFailed(dedupKey, clock)) {
                onDuplicateSkipped(event);
                return;
            }
        }
        try {
            deliver(event);
            // Terminal reservation (audit P2): retention deletes only
            // non-PENDING handler keys, so success must leave the key
            // COMPLETED behind — a perpetual PENDING key would accumulate
            // forever and defeat handler-key retention.
            idempotencyPort.markCompleted(dedupKey, event.eventType(), 200);
        } catch (Exception e) {
            // Audit P2: PENDING→FAILED so a later retry reopens the key via
            // tryResetFailed instead of mistaking it for a delivered duplicate
            // (which would mark the event processed without delivery).
            //
            // Transaction note: OutboxProcessor invokes relays inside its own
            // REQUIRES_NEW transaction, so on failure this FAILED write rolls
            // back together with the claim — the next retry tryCreates fresh
            // and redelivers (verified, no silent skip in the current wiring).
            // The FAILED branch above still matters for non-transactional
            // callers and survives any future split of the key lifecycle into
            // its own transaction.
            idempotencyPort.markFailed(dedupKey);
            onDeliveryFailed(event, e);
        }
    }

    private boolean reopenFailed(String dedupKey, Clock clock) {
        boolean failed = idempotencyPort.findById(dedupKey)
                .map(entry -> "FAILED".equals(entry.status()))
                .orElse(false);
        return failed && idempotencyPort.tryResetFailed(dedupKey, LocalDateTime.now(clock));
    }

    /**
     * Delivers a first-seen event. Any thrown exception reaches
     * {@link #onDeliveryFailed}.
     */
    protected abstract void deliver(EventEntry event) throws Exception;

    /**
     * Hook for duplicate skips. Default: silent (a duplicate is the normal
     * at-least-once outcome, not an incident).
     */
    protected void onDuplicateSkipped(EventEntry event) {
        // No-op by default; relays override to log their own line.
    }

    /**
     * Hook for delivery failures. Default: wraps with the concrete handler
     * name (relay tests assert on it) and rethrows so the outbox retries.
     */
    protected void onDeliveryFailed(EventEntry event, Exception failure) {
        throw new RuntimeException(getClass().getSimpleName() + " failed", failure);
    }
}
