package com.bank.app.transfer.adapter.in.config;

import com.bank.app.transfer.config.TransferProperties;
import com.bank.app.common.application.aspect.UseCaseAspectOrders;
import com.bank.app.common.domain.ExponentialBackoffPolicy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(UseCaseAspectOrders.TRANSFER_RETRY)
public class TransferUseCaseRetryAspect {

    /**
     * C-2/Q-6: interruptible sleep seam, mirroring
     * {@code IdempotentRetryExecutor.Sleeper}. Production parks the request
     * thread with the shared jittered policy; tests inject a no-op recorder.
     * A truly non-blocking wait is impossible here: the retry guards a
     * synchronous use-case call on the request thread, so the wait must stay
     * on this thread — the policy (shared with the idempotency path) is what
     * got unified, and only lock failures ever reach the sleep.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final TransferProperties transferProperties;
    private final Sleeper sleeper;

    @Autowired
    public TransferUseCaseRetryAspect(TransferProperties transferProperties) {
        this(transferProperties, delay -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // No re-interrupt here: around() restores the flag for every
                // sleep path (single ownership, no equivalent-mutant double).
                throw e;
            }
        });
    }

    // Test seam: explicit sleeper.
    TransferUseCaseRetryAspect(TransferProperties transferProperties, Sleeper sleeper) {
        this.transferProperties = transferProperties;
        this.sleeper = sleeper;
    }

    @Pointcut("execution(* com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl.execute(..))")
    void placeTransferMethod() {
    }

    @Pointcut("execution(* com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl.execute(..))")
    void cancelTransferMethod() {
    }

    @Around("placeTransferMethod() || cancelTransferMethod()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // The HTTP idempotency boundary retries the entire transaction. Retrying
        // only this use case inside an existing transaction would reuse a
        // rollback-only transaction after the first locking failure.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return joinPoint.proceed();
        }
        // C-2/T-6: same ExponentialBackoffPolicy as the idempotency path (single
        // retry policy, jittered). Only lock failures retry — 4xx never sleeps.
        ExponentialBackoffPolicy policy =
                new ExponentialBackoffPolicy(
                        Math.max(1, transferProperties.initialDelayMs()),
                        transferProperties.maxDelayMs(), 100);
        long delay = policy.initialDelayMs();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= transferProperties.maxAttempts(); attempt++) {
            try {
                return joinPoint.proceed();
            } catch (OptimisticLockingFailureException | PessimisticLockingFailureException e) {
                // No narrower listing needed: DeadlockLoserDataAccessException and
                // CannotAcquireLockException both extend PessimisticLockingFailureException,
                // so the two-account deadlock/lock-timeout path already retries here
                // (pinned by shouldRetryOnDeadlockLoser/shouldRetryOnCannotAcquireLock).
                lastException = e;
                if (attempt < transferProperties.maxAttempts()) {
                    try {
                        sleeper.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    delay = policy.nextDelay(delay);
                }
            }
        }

        // maxAttempts >= 1 (TransferProperties) guarantees lastException is set
        // whenever the loop exits without returning: only lock failures reach here.
        if (lastException == null) {
            throw new IllegalStateException("Retry failed unexpectedly");
        }
        throw lastException;
    }
}
