package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@Aspect
@Component
public class UseCaseTransactionAspect {

    /**
     * Upper bound for a single use-case transaction. Programmatic transactions do not
     * inherit Spring's {@code @Transactional(timeout=..)} semantics, so the timeout is
     * set explicitly to avoid hung row locks (e.g. PESSIMISTIC_WRITE on accounts).
     */
    private static final int TRANSACTION_TIMEOUT_SECONDS = 30;

    private final PlatformTransactionManager transactionManager;

    public UseCaseTransactionAspect(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Pointcut("@within(com.bank.app.common.application.port.in.ReadOnlyUseCase)")
    void readOnlyUseCaseMethod() {}

    @Pointcut("@within(com.bank.app.common.application.port.in.TransactionalUseCase)")
    void transactionalUseCaseMethod() {}

    @Pointcut("within(com.bank.app.audit.application.usecase..*)")
    void auditUseCaseMethod() {}

    @Around("transactionalUseCaseMethod() && !readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    public Object aroundReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setReadOnly(true);
        def.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
        return executeWithTransaction(joinPoint, def);
    }

    @Around("auditUseCaseMethod()")
    public Object aroundAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName(joinPoint.getSignature().toShortString());
        def.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
        return executeWithTransaction(joinPoint, def);
    }

    private Object executeWithTransaction(ProceedingJoinPoint joinPoint, DefaultTransactionDefinition def) throws Throwable {
        TransactionStatus status = transactionManager.getTransaction(def);
        boolean isNew = status.isNewTransaction();
        try {
            Object result = joinPoint.proceed();
            if (isNew) {
                transactionManager.commit(status);
            }
            return result;
        } catch (Throwable ex) {
            if (isNew) {
                transactionManager.rollback(status);
            }
            throw ex;
        }
    }
}
