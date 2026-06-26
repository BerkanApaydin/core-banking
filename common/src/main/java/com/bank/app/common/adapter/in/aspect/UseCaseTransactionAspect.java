package com.bank.app.common.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
public class UseCaseTransactionAspect {

    @Pointcut("within(@com.bank.app.common.application.ReadOnlyUseCase *)")
    void readOnlyUseCaseMethod() {}

    @Pointcut("within(@com.bank.app.common.application.UseCase *)")
    void useCaseMethod() {}

    @Pointcut("within(@com.bank.app.audit.application.usecase.*)")
    void auditUseCaseMethod() {}

    @Around("useCaseMethod() && !readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    @Transactional
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }

    @Around("readOnlyUseCaseMethod() && !auditUseCaseMethod()")
    @Transactional(readOnly = true)
    public Object aroundReadOnly(ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }

    @Around("auditUseCaseMethod()")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object aroundAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        return joinPoint.proceed();
    }
}
