package com.bank.app.infrastructure.adapter.in.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;

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
    private Signature signature;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private UseCaseTransactionAspect aspect;

    @Captor
    private ArgumentCaptor<DefaultTransactionDefinition> definitionCaptor;

    @BeforeEach
    void setUp() {
        aspect = new UseCaseTransactionAspect(transactionManager, new TransactionProperties(30));
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testSignature");
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
    void shouldApplyConfiguredTimeout() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint);

        verify(transactionManager).getTransaction(definitionCaptor.capture());
        assertEquals(30, definitionCaptor.getValue().getTimeout());
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
    void shouldCompleteParticipatingTransactionStatus() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldMarkParticipatingTransactionRollbackOnlyOnFailure() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("failed"));

        assertThrows(IllegalArgumentException.class, () -> aspect.around(joinPoint));

        verify(transactionManager).rollback(transactionStatus);
    }

    @Test
    void shouldPreserveOriginalCommitFailureWithoutSecondRollback() throws Throwable {
        var failingManager = new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
            @Override protected void doCommit(DefaultTransactionStatus status) {
                throw new TransactionSystemException("original commit failure");
            }
            @Override protected void doRollback(DefaultTransactionStatus status) {}
        };
        var realAspect = new UseCaseTransactionAspect(failingManager, new TransactionProperties(30));
        when(joinPoint.proceed()).thenReturn("result");

        TransactionSystemException failure = assertThrows(TransactionSystemException.class,
                () -> realAspect.around(joinPoint));

        assertEquals("original commit failure", failure.getMessage());
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
