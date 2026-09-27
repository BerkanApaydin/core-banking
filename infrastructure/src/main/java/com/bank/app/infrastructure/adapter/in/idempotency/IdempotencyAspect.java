package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.service.UserContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Order(1)
public class IdempotencyAspect {

    private final IdempotencyGuard idempotencyGuard;
    private final IdempotencyRequestResolver requestResolver;
    private final IdempotencyResponseCodec responseCodec;
    private final TransactionTemplate transactionTemplate;
    private final int maxAttempts;
    private final long initialDelayMs;

    public IdempotencyAspect(IdempotencyGuard idempotencyGuard,
            UserContextService userContextService,
            ObjectMapper objectMapper,
            ClientIpResolverPort clientIpResolver,
            PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties,
            @Value("${app.transfer.max-attempts:3}") int maxAttempts,
            @Value("${app.transfer.initial-delay-ms:500}") long initialDelayMs) {
        this.idempotencyGuard = idempotencyGuard;
        this.requestResolver = new IdempotencyRequestResolver(userContextService, clientIpResolver, objectMapper);
        this.responseCodec = new IdempotencyResponseCodec(objectMapper);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setTimeout(transactionProperties.timeoutSeconds());
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelayMs = Math.max(0, initialDelayMs);
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        IdempotencyRequestResolver.RequestIdentity identity = requestResolver.resolve(
                attributes.getRequest(), idempotent, joinPoint.getArgs());
        if (identity == null) {
            return joinPoint.proceed();
        }
        String key = identity.key();
        IdempotencyGuard.IdempotencyResult reservation = idempotencyGuard.startRequest(
                key, identity.requestHash());

        if (reservation.isCompleted()) {
            return responseCodec.replay(reservation);
        } else if (reservation.isPending()) {
            throw new ConcurrentRequestException("error.concurrent_request", null,
                    "This operation is currently being processed. Please wait.");
        }

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
                    delay = Math.min(delay * 2, 2_000L);
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
        private InvocationFailure(Throwable cause) { super(cause); }
    }

}
