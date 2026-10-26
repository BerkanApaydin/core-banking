package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bank.app.common.domain.exception.CommitOnFailure;
import com.bank.app.infrastructure.config.TransactionProperties;
import com.bank.app.common.application.aspect.UseCaseAspectOrders;

@Aspect
@Component
@Order(UseCaseAspectOrders.USE_CASE_TRANSACTION)
public class UseCaseTransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(UseCaseTransactionAspect.class);

    /**
     * Upper bound for a single use-case transaction. Programmatic transactions do not
     * inherit Spring's {@code @Transactional(timeout=..)} semantics, so the timeout is
     * set explicitly (see {@code app.transaction.timeout-seconds}) to avoid hung row
     * locks (e.g. PESSIMISTIC_WRITE on accounts).
     */
    private final int transactionTimeoutSeconds;

    private final PlatformTransactionManager transactionManager;

    public UseCaseTransactionAspect(PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties) {
        this.transactionManager = transactionManager;
        this.transactionTimeoutSeconds = transactionProperties.timeoutSeconds();
    }

    @Pointcut("@within(com.bank.app.common.application.port.in.ReadOnlyUseCase)")
    void readOnlyUseCaseMethod() {}

    @Pointcut("@within(com.bank.app.common.application.port.in.TransactionalUseCase)")
    void transactionalUseCaseMethod() {}

    // Isolation is explicit READ_COMMITTED (PostgreSQL default): the locking
    // strategy is pessimistic (SELECT ... FOR UPDATE in stable ID order), not
    // optimistic, so REPEATABLE_READ/SERIALIZABLE would only add 40001/40003
    // retries without benefit (9.2). Declared here — not a coincidence.
    @Around("transactionalUseCaseMethod() && !readOnlyUseCaseMethod()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("readOnlyUseCaseMethod()")
    public Object aroundReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setReadOnly(true);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    private Object executeWithTransaction(ProceedingJoinPoint joinPoint, DefaultTransactionDefinition def) throws Throwable {
        // P-1: PROPAGATION_REQUIRED joins an existing transaction and silently
        // ignores the inner isolation/timeout hints. Nested @TransactionalUseCase
        // calls (PlaceTransfer -> AdjustAccountBalances) are safe on a single
        // DataSource, but a second DataSource/saga would break here — log the
        // join so the future split has a grep-able tripwire.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Joining existing transaction for {} (propagation REQUIRED; inner isolation/timeout hints ignored)",
                    joinPoint.getSignature().toShortString());
        }
        TransactionStatus status = transactionManager.getTransaction(def);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            // CommitOnFailure: the exception reports a completed security
            // response whose DB side effects must survive (for example a
            // refresh-token theft response that revoked the family before
            // returning 401). Any other failure rolls back as usual, so an
            // audit-store outage after the revoke still aborts it.
            if (ex instanceof CommitOnFailure) {
                try {
                    transactionManager.commit(status);
                } catch (Throwable commitFailure) {
                    commitFailure.addSuppressed(ex);
                    throw commitFailure;
                }
                throw ex;
            }
            try {
                transactionManager.rollback(status);
            } catch (Throwable rollbackFailure) {
                rollbackFailure.addSuppressed(ex);
                throw rollbackFailure;
            }
            throw ex;
        }
        // Spring completes the status even when commit fails. Calling rollback
        // after such a failure would hide the original commit exception.
        transactionManager.commit(status);
        return result;
    }
}
