package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Claim step of the {@code @Idempotent} guard (K5/D15 split): resolves the
 * request identity and reserves the idempotency key, then reports a decision.
 * Knows nothing about transactions, retries or response encoding.
 */
class IdempotencyClaimer {

    sealed interface Decision permits Decision.Proceed, Decision.Replay, Decision.Contended, Decision.Guarded {
        record Proceed() implements Decision {}
        record Replay(IdempotencyGuard.IdempotencyResult reservation) implements Decision {}
        record Contended() implements Decision {}
        record Guarded(String key) implements Decision {}
    }

    private final IdempotencyGuard idempotencyGuard;
    private final IdempotencyRequestResolver requestResolver;

    IdempotencyClaimer(IdempotencyGuard idempotencyGuard, IdempotencyRequestResolver requestResolver) {
        this.idempotencyGuard = idempotencyGuard;
        this.requestResolver = requestResolver;
    }

    Decision claim(ServletRequestAttributes attributes, Idempotent idempotent, Object[] args)
            throws Throwable {
        IdempotencyRequestResolver.RequestIdentity identity =
                requestResolver.resolve(attributes.getRequest(), idempotent, args);
        if (identity == null) {
            return new Decision.Proceed();
        }
        IdempotencyGuard.IdempotencyResult reservation =
                idempotencyGuard.startRequest(identity.key(), identity.requestHash());
        if (reservation.isCompleted()) {
            return new Decision.Replay(reservation);
        }
        if (reservation.isPending()) {
            return new Decision.Contended();
        }
        return new Decision.Guarded(identity.key());
    }
}
