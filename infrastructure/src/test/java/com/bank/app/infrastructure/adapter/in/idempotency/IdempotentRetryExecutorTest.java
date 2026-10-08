package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.domain.ExponentialBackoffPolicy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bounded-retry contract for the {@code @Idempotent} guard execution step.
 *
 * <p>Only lock failures are retried, with the delay sequence driven by a
 * single {@link ExponentialBackoffPolicy}; every other failure fails the key
 * fast so a stuck {@code PENDING} reservation can be reclaimed. The wait is
 * delegated to a recording {@code Sleeper}, so no test ever sleeps.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotentRetryExecutor")
@SuppressWarnings("null")
class IdempotentRetryExecutorTest {

    private static final String KEY = "idem-key-1";

    @Mock
    private IdempotencyGuard idempotencyGuard;

    @Mock
    private IdempotencyResponseCodec responseCodec;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private TransactionStatus transactionStatus;

    private List<Long> sleptDelays;
    private IdempotentRetryExecutor executor;

    @BeforeEach
    void setUp() {
        sleptDelays = new ArrayList<>();
        executor = new IdempotentRetryExecutor(
                idempotencyGuard,
                responseCodec,
                transactionTemplate,
                3,
                new ExponentialBackoffPolicy(100, 2_000, 0),
                sleptDelays::add);
    }

    @Test
    @DisplayName("should complete the key on a successful invocation")
    void shouldCompleteOnSuccess() throws Throwable {
        // Arrange
        ResponseEntity<String> response = ResponseEntity.ok("created");
        when(joinPoint.proceed()).thenReturn(response);
        when(responseCodec.serialize(response))
                .thenReturn(new IdempotencyResponseCodec.StoredResponse("{\"ok\":true}", 200, true));
        runInTransaction();

        // Act
        Object result = executor.execute(KEY, joinPoint);

        // Assert
        assertThat(result).isSameAs(response);
        verify(idempotencyGuard, times(1)).completeRequest(KEY, "{\"ok\":true}", 200);
        verify(idempotencyGuard, never()).failRequest(anyString());
        assertThat(sleptDelays).isEmpty();
    }

    @Test
    @DisplayName("should fail the key without retry when the response is not 2xx")
    void shouldFailOnUnsuccessfulResponse() throws Throwable {
        // Arrange
        ResponseEntity<String> response = ResponseEntity.status(409).body("conflict");
        when(joinPoint.proceed()).thenReturn(response);
        when(responseCodec.serialize(response))
                .thenReturn(new IdempotencyResponseCodec.StoredResponse("", 409, false));
        runInTransaction();

        // Act
        Object result = executor.execute(KEY, joinPoint);

        // Assert
        assertThat(result).isSameAs(response);
        verify(transactionStatus, times(1)).setRollbackOnly();
        verify(idempotencyGuard, times(1)).failRequest(KEY);
        verify(idempotencyGuard, never()).completeRequest(anyString(), anyString(), anyInt());
        assertThat(sleptDelays).isEmpty();
    }

    @Test
    @DisplayName("should retry lock failures with the policy delay sequence")
    void shouldRetryLockFailures() throws Throwable {
        // Arrange
        ResponseEntity<String> response = ResponseEntity.ok("created");
        when(joinPoint.proceed())
                .thenThrow(new OptimisticLockingFailureException("version conflict"))
                .thenThrow(new PessimisticLockingFailureException("row lock"))
                .thenReturn(response);
        when(responseCodec.serialize(response))
                .thenReturn(new IdempotencyResponseCodec.StoredResponse("{}", 200, true));
        runInTransaction();

        // Act
        Object result = executor.execute(KEY, joinPoint);

        // Assert
        assertThat(result).isSameAs(response);
        assertThat(sleptDelays).containsExactly(100L, 200L);
        verify(idempotencyGuard, times(1)).completeRequest(eq(KEY), anyString(), anyInt());
        verify(idempotencyGuard, never()).failRequest(anyString());
    }

    @Test
    @DisplayName("should fail fast on non-retryable failures")
    void shouldFailFastOnNonRetryable() throws Throwable {
        // Arrange
        IllegalStateException businessFailure = new IllegalStateException("rule violated");
        when(joinPoint.proceed()).thenThrow(businessFailure);
        runInTransaction();

        // Act + Assert
        assertThatThrownBy(() -> executor.execute(KEY, joinPoint))
                .isSameAs(businessFailure);
        verify(idempotencyGuard, times(1)).failRequest(KEY);
        verify(idempotencyGuard, never()).completeRequest(anyString(), anyString(), anyInt());
        assertThat(sleptDelays).isEmpty();
    }

    @Test
    @DisplayName("should give up after exhausting attempts")
    void shouldGiveUpAfterMaxAttempts() throws Throwable {
        // Arrange — a 2-attempt executor that always hits a lock conflict.
        executor = new IdempotentRetryExecutor(
                idempotencyGuard, responseCodec, transactionTemplate, 2,
                new ExponentialBackoffPolicy(100, 2_000, 0), sleptDelays::add);
        OptimisticLockingFailureException conflict =
                new OptimisticLockingFailureException("persistent conflict");
        when(joinPoint.proceed()).thenThrow(conflict);
        runInTransaction();

        // Act + Assert
        assertThatThrownBy(() -> executor.execute(KEY, joinPoint))
                .isSameAs(conflict);
        assertThat(sleptDelays).containsExactly(100L);
        verify(idempotencyGuard, times(1)).failRequest(KEY);
    }

    @Test
    @DisplayName("should retain PENDING when the commit outcome is unknown")
    void shouldRetainPendingOnCommitFailure() throws Throwable {
        // Arrange — the callback succeeds but the commit itself throws: the
        // outcome is unknown, so the key must stay PENDING for a later probe
        // instead of being failed or retried.
        OptimisticLockingFailureException commitFailure =
                new OptimisticLockingFailureException("commit conflict");
        when(transactionTemplate.execute(any())).thenThrow(commitFailure);

        // Act + Assert
        assertThatThrownBy(() -> executor.execute(KEY, joinPoint))
                .isSameAs(commitFailure);
        verify(idempotencyGuard, never()).failRequest(anyString());
        verify(idempotencyGuard, never()).completeRequest(anyString(), anyString(), anyInt());
        assertThat(sleptDelays).isEmpty();
    }

    @Test
    @DisplayName("should reject a transaction that produces no result")
    void shouldRejectNullTransactionResult() throws Throwable {
        // Arrange
        when(transactionTemplate.execute(any())).thenReturn(null);

        // Act + Assert
        assertThatThrownBy(() -> executor.execute(KEY, joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no transaction result");
    }

    @Test
    @DisplayName("should fail the key and restore interrupt when sleep is interrupted")
    void shouldHandleInterruptedSleep() throws Throwable {
        // Arrange
        when(joinPoint.proceed())
                .thenThrow(new OptimisticLockingFailureException("conflict"));
        runInTransaction();
        InterruptedException interruption = new InterruptedException("shutdown");
        IdempotentRetryExecutor interrupting = new IdempotentRetryExecutor(
                idempotencyGuard, responseCodec, transactionTemplate, 3,
                new ExponentialBackoffPolicy(100, 2_000, 0),
                delay -> { throw interruption; });

        // Act + Assert
        assertThatThrownBy(() -> interrupting.execute(KEY, joinPoint))
                .isSameAs(interruption);
        verify(idempotencyGuard, times(1)).failRequest(KEY);
        // The executor restores the interrupt flag before propagating; the
        // assertion consumes (clears) it so the worker thread stays clean.
        assertThat(Thread.interrupted()).isTrue();
    }

    /** Runs the transaction callback inline against the mocked status. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void runInTransaction() {
        when(transactionTemplate.execute(any())).thenAnswer((Answer<Object>) invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback =
                    invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }
}
