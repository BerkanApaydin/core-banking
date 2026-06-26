package com.bank.app.transfer.adapter.config;

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

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 500;
    private static final long MAX_DELAY_MS = 2000;

    @Pointcut("execution(* com.bank.app.transfer.application.usecase.PlaceTransferUseCaseImpl.execute(..))")
    void placeTransferMethod() {
    }

    @Pointcut("execution(* com.bank.app.transfer.application.usecase.CancelTransferUseCaseImpl.execute(..))")
    void cancelTransferMethod() {
    }

    @Around("placeTransferMethod() || cancelTransferMethod()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long delay = INITIAL_DELAY_MS;
        Throwable lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return joinPoint.proceed();
            } catch (OptimisticLockingFailureException e) {
                lastException = e;
                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, MAX_DELAY_MS);
                }
            }
        }

        throw Optional.ofNullable(lastException)
                .orElseThrow(() -> new IllegalStateException("Retry failed unexpectedly"));
    }
}
