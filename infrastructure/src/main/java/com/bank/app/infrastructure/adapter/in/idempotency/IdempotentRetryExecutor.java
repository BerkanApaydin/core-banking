package com.bank.app.infrastructure.adapter.in.idempotency;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Execution step of the {@code @Idempotent} guard (K5/D15 split): runs the
 * guarded invocation inside the idempotency transaction with bounded
 * optimistic/pessimistic-lock retries. Knows nothing about key reservation
 * or response replay.
 */
class IdempotentRetryExecutor {

    private static final long MAX_BACKOFF_MS = 2_000L;

    private final IdempotencyGuard idempotencyGuard;
    private final IdempotencyResponseCodec responseCodec;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final long initialDelayMs;

    IdempotentRetryExecutor(IdempotencyGuard idempotencyGuard,
                            IdempotencyResponseCodec responseCodec,
                            TransactionTemplate transactionTemplate,
                            int maxAttempts,
                            long initialDelayMs) {
        this.idempotencyGuard = idempotencyGuard;
        this.responseCodec = responseCodec;
        this.transactionTemplate = transactionTemplate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelayMs = Math.max(0, initialDelayMs);
    }

    Object execute(String key, ProceedingJoinPoint joinPoint) throws Throwable {
        long delay = initialDelayMs;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ExecutionResult result = transactionTemplate.execute(status -> {
                    try {
                        Object response = joinPoint.proceed();
                        IdempotencyResponseCodec.StoredResponse stored = responseCodec.serialize(response);
                        if (!stored.successful()) {
                            status.setRollbackOnly();
                            return new ExecutionResult(response, false);
                        }
                        idempotencyGuard.completeRequest(key, stored.body(), stored.status());
                        return new ExecutionResult(response, true);
                    } catch (Throwable failure) {
                        throw new InvocationFailure(failure);
                    }
                });
                if (result == null) {
                    throw new IllegalStateException("Idempotent operation produced no transaction result");
                }
                if (!result.successful()) {
                    idempotencyGuard.failRequest(key);
                }
                return result.response();
            } catch (InvocationFailure invocationFailure) {
                Throwable original = invocationFailure.getCause();
                if (isRetryable(original) && attempt < maxAttempts) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        try {
                            failAfterRollback(key, interrupted);
                        } finally {
                            Thread.currentThread().interrupt();
                        }
                        throw interrupted;
                    }
                    delay = Math.min(delay * 2, MAX_BACKOFF_MS);
                    continue;
                }
                // The transaction callback failed and was rolled back. An
                // exception during commit takes a different path below, where
                // the outcome is unknown and PENDING must be retained.
                failAfterRollback(key, original);
                throw original;
            }
        }
        throw new IllegalStateException("Idempotent operation exhausted retry attempts");
    }

    private void failAfterRollback(String key, Throwable original) {
        try {
            idempotencyGuard.failRequest(key);
        } catch (RuntimeException cleanupFailure) {
            // Preserve the failure that caused rollback for callers and diagnostics.
            if (cleanupFailure != original) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private static boolean isRetryable(Throwable failure) {
        return failure instanceof OptimisticLockingFailureException
                || failure instanceof PessimisticLockingFailureException;
    }

    private record ExecutionResult(Object response, boolean successful) {}

    private static final class InvocationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private InvocationFailure(Throwable cause) { super(cause); }
    }
}
