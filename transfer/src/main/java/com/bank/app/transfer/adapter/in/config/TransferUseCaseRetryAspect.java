package com.bank.app.transfer.adapter.in.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
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
        long delay = transferProperties.initialDelayMs();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= transferProperties.maxAttempts(); attempt++) {
            try {
                return joinPoint.proceed();
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                if (attempt < transferProperties.maxAttempts()) {
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, transferProperties.maxDelayMs());
                }
            }
        }

        throw Optional.ofNullable(lastException)
                .orElseThrow(() -> new IllegalStateException("Retry failed unexpectedly"));
    }
}
