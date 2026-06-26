package com.bank.app.common.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UseCaseTransactionAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private final UseCaseTransactionAspect aspect = new UseCaseTransactionAspect();

    @Test
    void shouldProceedOnAround() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint);

        verify(joinPoint).proceed();
    }

    @Test
    void shouldProceedOnAroundReadOnly() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.aroundReadOnly(joinPoint);

        verify(joinPoint).proceed();
    }

    @Test
    void shouldProceedOnAroundAudit() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.aroundAudit(joinPoint);

        verify(joinPoint).proceed();
    }
}
