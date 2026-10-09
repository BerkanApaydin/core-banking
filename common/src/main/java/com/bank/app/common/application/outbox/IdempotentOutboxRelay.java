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
 * change, or already-processed events reprocess after a deploy.
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
        if (!idempotencyPort.tryCreate(dedupKeyPrefix + event.id(), LocalDateTime.now(clock))) {
            onDuplicateSkipped(event);
            return;
        }
        try {
            deliver(event);
        } catch (Exception e) {
            onDeliveryFailed(event, e);
        }
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
