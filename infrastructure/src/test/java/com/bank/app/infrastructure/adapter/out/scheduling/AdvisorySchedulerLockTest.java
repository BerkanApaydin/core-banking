package com.bank.app.infrastructure.adapter.out.scheduling;

import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvisorySchedulerLock")
class AdvisorySchedulerLockTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus status;

    private AdvisorySchedulerLock lock() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        return new AdvisorySchedulerLock(jdbc, new TransactionTemplate(transactionManager));
    }

    @Test
    @DisplayName("should run work when the lock is acquired (K12/D5)")
    void shouldRunWhenLockAcquired() {
        when(jdbc.queryForObject(eq(AdvisorySchedulerLock.LOCK_SQL), eq(Boolean.class), eq("job")))
                .thenReturn(Boolean.TRUE);
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = lock().runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("should skip work when another replica holds the lock (K12/D5)")
    void shouldSkipWhenLockHeld() {
        when(jdbc.queryForObject(eq(AdvisorySchedulerLock.LOCK_SQL), eq(Boolean.class), eq("job")))
                .thenReturn(Boolean.FALSE);
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = lock().runIfLeader("job", () -> ran.set(true));

        assertThat(result).isFalse();
        assertThat(ran).isFalse();
        verify(status).setRollbackOnly();
    }

    @Test
    @DisplayName("should fail open when the lock query errors (K12/D5)")
    void shouldFailOpenOnLockQueryError() {
        when(jdbc.queryForObject(eq(AdvisorySchedulerLock.LOCK_SQL), eq(Boolean.class), eq("job")))
                .thenThrow(new RuntimeException("lock query down"));
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = lock().runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        // The poisoned lock transaction must be rolled back before the work
        // runs; otherwise every statement in work sees PostgreSQL's
        // "current transaction is aborted" state.
        verify(status).setRollbackOnly();
    }

    @Test
    @DisplayName("alwaysRun should execute without touching the database")
    void alwaysRunShouldExecuteInline() {
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = AdvisorySchedulerLock.alwaysRun().runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    @DisplayName("should honor the configured timeout from properties")
    void shouldHonorConfiguredTimeoutFromProperties() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbc.queryForObject(eq(AdvisorySchedulerLock.LOCK_SQL), eq(Boolean.class), eq("job")))
                .thenReturn(Boolean.TRUE);
        var lock = new AdvisorySchedulerLock(jdbc, transactionManager, new TransactionProperties(17));
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = lock.runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(definition.getValue().getTimeout()).isEqualTo(17);
    }

    @Test
    @DisplayName("should use the legacy 30s budget for the two-arg constructor")
    void shouldUseLegacyDefaultTimeout() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbc.queryForObject(eq(AdvisorySchedulerLock.LOCK_SQL), eq(Boolean.class), eq("job")))
                .thenReturn(Boolean.TRUE);
        var lock = new AdvisorySchedulerLock(jdbc, transactionManager);
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = lock.runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(definition.getValue().getTimeout()).isEqualTo(30);
    }
}
