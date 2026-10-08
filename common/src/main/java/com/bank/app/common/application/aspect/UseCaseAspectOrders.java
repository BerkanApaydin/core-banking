package com.bank.app.common.application.aspect;

/**
 * Single source of truth for use-case aspect precedence.
 *
 * <p>Lives in {@code common} so both the programmatic transaction aspect
 * (infrastructure) and the transfer retry aspect (transfer) can share it
 * without a module-boundary violation. Values mirror
 * {@code org.springframework.core.Ordered#LOWEST_PRECEDENCE} without
 * depending on Spring (common stays framework-free). Required order (lowest
 * value = outermost): idempotency ({@code HIGHEST_PRECEDENCE + 1}) &gt;
 * transfer retry &gt; programmatic transaction. The retry aspect probes
 * {@code TransactionSynchronizationManager.isActualTransactionActive()} to
 * avoid reusing a rollback-only transaction — that probe is only meaningful
 * while this ordering holds. Covered by {@code AspectOrderingTest}.
 *
 * <p>Scope note: this ordering covers the programmatic model only
 * ({@code UseCaseTransactionAspect}). {@code IdempotencyGuard} is deliberately
 * outside it — it uses declarative Spring {@code @Transactional} with
 * {@code REQUIRES_NEW} so the idempotency claim survives the outer
 * transaction's rollback. Do not "unify" the two without an ADR: merging the
 * guard into the programmatic model would couple claim durability to business
 * rollback, which is exactly what the split prevents.
 */
public final class UseCaseAspectOrders {

    private UseCaseAspectOrders() {}

    private static final int LOWEST_PRECEDENCE = Integer.MAX_VALUE;

    /** Transfer retry must wrap the transaction boundary, not run inside it. */
    public static final int TRANSFER_RETRY = LOWEST_PRECEDENCE - 200;

    /** Programmatic REQUIRED/READ_COMMITTED boundary for use cases. */
    public static final int USE_CASE_TRANSACTION = LOWEST_PRECEDENCE - 100;
}
