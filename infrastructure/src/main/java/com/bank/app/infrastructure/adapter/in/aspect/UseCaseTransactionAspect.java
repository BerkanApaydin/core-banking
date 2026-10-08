package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;
import com.bank.app.common.application.aspect.UseCaseAspectOrders;

@Aspect
@Component
@Order(UseCaseAspectOrders.USE_CASE_TRANSACTION)
public class UseCaseTransactionAspect {

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

    @Pointcut("within(com.bank.app.audit.application.usecase..*)")
    void auditUseCaseMethod() {}

    // Isolation is explicit READ_COMMITTED (PostgreSQL default): the locking
    // strategy is pessimistic (SELECT ... FOR UPDATE in stable ID order), not
    // optimistic, so REPEATABLE_READ/SERIALIZABLE would only add 40001/40003
    // retries without benefit (9.2). Declared here — not a coincidence.
    @Around("transactionalUseCaseMethod() && !readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    public Object aroundReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setReadOnly(true);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("auditUseCaseMethod() && !readOnlyUseCaseMethod()")
    public Object aroundAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("auditUseCaseMethod() && readOnlyUseCaseMethod()")
    public Object aroundAuditReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setReadOnly(true);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setTimeout(transactionTimeoutSeconds);
        return executeWithTransaction(joinPoint, def);
    }

    private Object executeWithTransaction(ProceedingJoinPoint joinPoint, DefaultTransactionDefinition def) throws Throwable {
        TransactionStatus status = transactionManager.getTransaction(def);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
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
