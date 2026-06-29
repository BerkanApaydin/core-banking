package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class UseCaseTransactionAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private org.aspectj.lang.Signature signature;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private UseCaseTransactionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new UseCaseTransactionAspect(transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testSignature");

    }

    @Test
    void shouldProceedOnAround() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint);

        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldProceedOnAroundReadOnly() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.aroundReadOnly(joinPoint);

        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldProceedOnAroundAudit() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.aroundAudit(joinPoint);

        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);
    }
}
