package com.bank.app.transfer.adapter.in.config;

import com.bank.app.transfer.config.TransferProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class TransferUseCaseRetryAspect {

    private final TransferProperties transferProperties;

    public TransferUseCaseRetryAspect(TransferProperties transferProperties) {
        this.transferProperties = transferProperties;
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
        long delay = transferProperties.initialDelayMs();
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
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                    delay = Math.min(delay * 2, transferProperties.maxDelayMs());
                }
            }
        }

        throw Optional.ofNullable(lastException)
                .orElseThrow(() -> new IllegalStateException("Retry failed unexpectedly"));
    }
}
