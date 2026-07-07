package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Captor
    private ArgumentCaptor<DefaultTransactionDefinition> definitionCaptor;

    @BeforeEach
    void setUp() {
        aspect = new UseCaseTransactionAspect(transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testSignature");
        when(transactionStatus.isNewTransaction()).thenReturn(true);
    }

    @Test
    void shouldProceedOnAround() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldProceedOnAroundReadOnly() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundReadOnly(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(true, def.isReadOnly());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRollbackOnException() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> aspect.around(joinPoint));
        assertEquals("test error", ex.getMessage());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldNotCommitWhenTransactionIsNotNew() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");
        when(transactionStatus.isNewTransaction()).thenReturn(false);

        Object result = aspect.around(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager, never()).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldProceedOnAroundAudit() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundAudit(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW, def.getPropagationBehavior());
        verify(transactionManager).commit(transactionStatus);
    }
}
