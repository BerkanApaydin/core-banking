package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.messages.MessageKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.bank.app.common.adapter.in.web.ClientIpResolver;
import com.bank.app.common.application.aspect.UseCaseAspectOrders;
import com.bank.app.infrastructure.config.IdempotencyProperties;
import com.bank.app.infrastructure.config.TransactionProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Aspect
@Component
@Order(UseCaseAspectOrders.IDEMPOTENCY)
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyClaimer claimer;
    private final IdempotentRetryExecutor retryExecutor;
    private final IdempotencyResponseCodec responseCodec;

    public IdempotencyAspect(IdempotencyGuard idempotencyGuard,
            UserContextService userContextService,
            ObjectMapper objectMapper,
            ClientIpResolver clientIpResolver,
            PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties,
            IdempotencyProperties idempotencyProperties) {
        IdempotencyRequestResolver requestResolver =
                new IdempotencyRequestResolver(userContextService, clientIpResolver, objectMapper);
        this.responseCodec = new IdempotencyResponseCodec(objectMapper);
        this.claimer = new IdempotencyClaimer(idempotencyGuard, requestResolver);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setTimeout(transactionProperties.timeoutSeconds());
        this.retryExecutor = new IdempotentRetryExecutor(idempotencyGuard, responseCodec,
                transactionTemplate, idempotencyProperties.maxAttempts(),
                idempotencyProperties.initialDelayMs());
    }

    // K5/D15: the 70-line handler is now claim → decide → execute.
    // P-2: non-HTTP invocations (no RequestContextHolder: scheduler, direct
    // unit calls) cannot carry an Idempotency-Key. Required keys fail closed
    // so a wiring mistake cannot silently bypass protection; optional keys
    // keep the grep-able log-and-proceed tripwire.
    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            if (idempotent.required()) {
                throw new IllegalStateException(
                        "Idempotency-Key is required, but no HTTP request context is bound");
            }
            log.warn("Idempotency bypassed: no HTTP request context bound; proceeding without claim");
            return joinPoint.proceed();
        }
        return switch (claimer.claim(attributes, idempotent, joinPoint.getArgs())) {
            case IdempotencyClaimer.Decision.Proceed ignored -> joinPoint.proceed();
            case IdempotencyClaimer.Decision.Replay replay -> responseCodec.replay(replay.reservation());
            case IdempotencyClaimer.Decision.Contended ignored -> throw new ConcurrentRequestException(
                    MessageKeys.CONCURRENT_REQUEST, null,
                    "This operation is currently being processed. Please wait.");
            case IdempotencyClaimer.Decision.Guarded guarded ->
                    retryExecutor.execute(guarded.key(), joinPoint);
        };
    }
}
