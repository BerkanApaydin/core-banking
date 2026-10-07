package com.bank.app.infrastructure.adapter.out.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    }

    @Test
    @DisplayName("alwaysRun should execute without touching the database")
    void alwaysRunShouldExecuteInline() {
        AtomicBoolean ran = new AtomicBoolean();

        boolean result = AdvisorySchedulerLock.alwaysRun().runIfLeader("job", () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }
}
