package com.bank.app.common.application.port.out;

import java.util.function.Supplier;

/**
 * Framework-free view over the ambient transaction boundary.
 *
 * <p>Bounded contexts must never import Spring transaction support classes
 * ({@code TransactionSynchronizationManager}, {@code TransactionTemplate},
 * {@code PlatformTransactionManager}) — that pins domain-facing adapters to
 * Spring and hides transaction control inside business modules. All such
 * control lives behind this port; the single Spring-based implementation sits
 * in {@code infrastructure} next to the other transaction machinery.
 *
 * <p>Deliberately narrow: reads ({@link #isTransactionActive}), commit hooks
 * ({@link #runAfterCommit}) and row-scoped work ({@link #executeRequiresNew}).
 * Use-case demarcation itself stays with the {@code @TransactionalUseCase}
 * markers and {@code UseCaseTransactionAspect} — this port is for adapters
 * that need to <em>observe</em> the boundary (deferred cache eviction,
 * retry suppression, crash-window reaping), not to open business
 * transactions.
 *
 * <p>Persistence failure <em>types</em> ({@code OptimisticLockingFailureException}
 * et al.) are intentionally NOT abstracted here: they are the shared failure
 * taxonomy thrown by the persistence adapters and mapped centrally by the
 * infrastructure problem handlers.
 */
public interface TransactionBoundaryPort {

    /**
     * Whether the calling thread currently runs inside a transaction.
     */
    boolean isTransactionActive();

    /**
     * Runs {@code action} after the current transaction commits. When no
     * transaction is active the action runs immediately. Used for effects that
     * must never fire on rollback (e.g. cache eviction of still-valid
     * snapshots).
     */
    void runAfterCommit(Runnable action);

    /**
     * Runs {@code action} in its own transaction with the given timeout, and
     * returns its result. Runtime exceptions propagate unchanged. Used for
     * row-scoped background work (e.g. crash-window reaping) where one
     * poisoned row must not roll back the rest of the batch.
     *
     * @param timeoutSeconds transaction timeout; must be positive
     */
    <T> T executeRequiresNew(Supplier<T> action, int timeoutSeconds);
}
