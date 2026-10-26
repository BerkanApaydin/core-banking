package com.bank.app.infrastructure.adapter.in.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.bank.app.common.domain.exception.CommitOnFailure;
import com.bank.app.infrastructure.config.TransactionProperties;
import com.bank.app.infrastructure.testsupport.LogCapture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testSignature");
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    @Test
    void shouldProceedOnAround() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, def.getIsolationLevel());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldApplyConfiguredTimeout() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.around(joinPoint);

        verify(transactionManager).getTransaction(definitionCaptor.capture());
        assertEquals(30, definitionCaptor.getValue().getTimeout());
    }

    @Test
    void shouldLogWhenJoiningAnExistingTransaction() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");
        Logger logger = (Logger) LoggerFactory.getLogger(UseCaseTransactionAspect.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (LogCapture logs = LogCapture.attach(UseCaseTransactionAspect.class)) {
            aspect.around(joinPoint);

            assertTrue(logs.messages().stream()
                    .anyMatch(message -> message.contains("Joining existing transaction")));
        } finally {
            TransactionSynchronizationManager.clear();
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void shouldProceedOnAroundReadOnly() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundReadOnly(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(true, def.isReadOnly());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, def.getIsolationLevel());
        assertEquals(30, def.getTimeout());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldRollbackOnException() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> aspect.around(joinPoint));
        assertEquals("test error", ex.getMessage());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldPreserveOriginalFailureWhenRollbackItselfFails() throws Throwable {
        // A broken transaction manager during rollback must surface as a
        // TransactionSystemException while keeping the real business failure
        // attached, otherwise the incident loses its root cause.
        stubTransaction();
        RuntimeException businessFailure = new RuntimeException("business failed");
        TransactionSystemException rollbackFailure =
                new TransactionSystemException("rollback failed");
        when(joinPoint.proceed()).thenThrow(businessFailure);
        doThrow(rollbackFailure).when(transactionManager).rollback(transactionStatus);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> aspect.around(joinPoint));

        assertEquals(rollbackFailure, thrown);
        assertEquals(businessFailure, thrown.getSuppressed()[0]);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void shouldCommitWhenTheFailureIsACommittedSecurityResponse() throws Throwable {
        stubTransaction();
        CommittedResponseFailure failure = new CommittedResponseFailure();
        when(joinPoint.proceed()).thenThrow(failure);

        CommittedResponseFailure thrown = assertThrows(CommittedResponseFailure.class,
                () -> aspect.around(joinPoint));

        assertEquals(failure, thrown);
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldRollbackWhenCommitOnFailureCommitItselfFails() throws Throwable {
        stubTransaction();
        CommittedResponseFailure failure = new CommittedResponseFailure();
        when(joinPoint.proceed()).thenThrow(failure);
        doThrow(new TransactionSystemException("commit failed"))
                .when(transactionManager).commit(transactionStatus);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> aspect.around(joinPoint));

        assertEquals("commit failed", thrown.getMessage());
        assertEquals(failure, thrown.getSuppressed()[0]);
    }

    private static final class CommittedResponseFailure extends RuntimeException
            implements CommitOnFailure {
        private static final long serialVersionUID = 1L;
    }

    @Test
    void shouldCompleteParticipatingTransactionStatus() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.around(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldMarkParticipatingTransactionRollbackOnlyOnFailure() throws Throwable {
        stubTransaction();
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
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundAudit(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW, def.getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, def.getIsolationLevel());
        assertEquals(30, def.getTimeout());
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void shouldProceedOnAroundAuditReadOnly() throws Throwable {
        stubTransaction();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.aroundAuditReadOnly(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(transactionManager).getTransaction(definitionCaptor.capture());
        DefaultTransactionDefinition def = definitionCaptor.getValue();
        assertEquals("testSignature", def.getName());
        assertEquals(DefaultTransactionDefinition.PROPAGATION_REQUIRES_NEW, def.getPropagationBehavior());
        assertEquals(true, def.isReadOnly());
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, def.getIsolationLevel());
        assertEquals(30, def.getTimeout());
        verify(transactionManager).commit(transactionStatus);
    }
}
