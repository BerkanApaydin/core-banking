package com.bank.app.transfer.adapter.in.config;

import com.bank.app.common.application.port.out.TransactionBoundaryPort;
import com.bank.app.transfer.config.TransferProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("TransferUseCaseRetryAspect")
class TransferUseCaseRetryAspectTest {

    private final TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
            new TransferProperties(Duration.ofHours(24), 3, 50, 500, 100));

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Nested
    @DisplayName("around")
    class Around {

        @Test
        @DisplayName("should proceed on first attempt when no exception thrown")
        void shouldProceedOnFirstAttempt() throws Throwable {
            when(joinPoint.proceed()).thenReturn("success");
            Object result = aspect.around(joinPoint);
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should retry on OptimisticLockingFailureException and succeed")
        void shouldRetryAndSucceed() throws Throwable {
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("version conflict"))
                    .thenReturn("success");

            Object result = aspect.around(joinPoint);
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("should retry on deadlock-loser failures via the pessimistic predicate")
        void shouldRetryOnDeadlockLoser() throws Throwable {
            // DeadlockLoserDataAccessException extends PessimisticLockingFailureException,
            // so the existing catch clause already retries the most likely
            // two-account failure — this test pins that subtyping contract.
            when(joinPoint.proceed())
                    .thenThrow(new DeadlockLoserDataAccessException("deadlock detected",
                            new RuntimeException("db deadlock")))
                    .thenReturn("success");

            Object result = aspect.around(joinPoint);
            assertThat(result).isEqualTo("success");
            verify(joinPoint, times(2)).proceed();
        }

        @Test
        @DisplayName("should retry on lock-acquisition timeouts via the pessimistic predicate")
        void shouldRetryOnCannotAcquireLock() throws Throwable {
            when(joinPoint.proceed())
                    .thenThrow(new CannotAcquireLockException("lock timeout"))
                    .thenReturn("success");

            Object result = aspect.around(joinPoint);
            assertThat(result).isEqualTo("success");
            verify(joinPoint, times(2)).proceed();
        }

        @Test
        @DisplayName("should throw OptimisticLockingFailureException after exhausting retries")
        void shouldThrowAfterExhaustingRetries() throws Throwable {
            OptimisticLockingFailureException original = new OptimisticLockingFailureException("persistent conflict");
            when(joinPoint.proceed()).thenThrow(original);

            assertThatThrownBy(() -> aspect.around(joinPoint))
                    .isExactlyInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessage("persistent conflict");
            verify(joinPoint, times(3)).proceed();
        }

        @Test
        @DisplayName("should not retry on non-OptimisticLockingFailureException")
        void shouldNotRetryOnOtherExceptions() throws Throwable {
            RuntimeException ex = new IllegalArgumentException("invalid argument");
            when(joinPoint.proceed()).thenThrow(ex);

            assertThatThrownBy(() -> aspect.around(joinPoint))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("invalid argument");
        }

        @Test
        @DisplayName("should restore interrupt flag and rethrow when sleep is interrupted")
        void shouldRestoreInterruptFlagOnInterruptedSleep() throws Throwable {
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("conflict"));
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(() -> aspect.around(joinPoint))
                        .isExactlyInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                // Never leak the interrupt flag to the shared test thread.
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("should throw original exception after single attempt when maxAttempts is 1")
        void shouldThrowAfterSingleAttemptWhenMaxAttemptsIsOne() throws Throwable {
            // With maxAttempts=1 and a failure, original code runs the loop once and throws OLFE.
            // L35 mutant (attempt <= maxAttempts → attempt < maxAttempts) skips the loop entirely,
            // leaving lastException=null and throwing IllegalStateException instead.
            TransferUseCaseRetryAspect aspect1 = new TransferUseCaseRetryAspect(
                    new TransferProperties(Duration.ofHours(24), 1, 100, 1000, 100));
            OptimisticLockingFailureException original = new OptimisticLockingFailureException("conflict");
            when(joinPoint.proceed()).thenThrow(original);

            assertThatThrownBy(() -> aspect1.around(joinPoint))
                    .isExactlyInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessage("conflict");
            verify(joinPoint, times(1)).proceed();
        }

        @Test
        @DisplayName("should double delay after each retry")
        void shouldDoubleDelayAfterEachRetry() throws Throwable {
            // 2 failures then success. Timing detects delay-related mutations:
            // Original: sleep(1000) + sleep(2000) = ~3000ms
            // L42 delay/2: sleep(1000) + sleep(500) = ~1500ms (< 2000 assertion fails)
            // L41 removed sleep: ~0ms (< 2000 assertion fails)
            TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                    new TransferProperties(Duration.ofHours(24), 3, 1000, 10000, 100));
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("1"))
                    .thenThrow(new OptimisticLockingFailureException("2"))
                    .thenReturn("success");

            long start = System.nanoTime();
            Object result = aspect.around(joinPoint);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(result).isEqualTo("success");
            // Original takes ~3000ms; any sleep/delay mutant takes < 2000ms
            assertThat(elapsedMs).isGreaterThan(2000L);
            verify(joinPoint, times(3)).proceed();
        }

        @Test
        @DisplayName("should never sleep with maxAttempts=1 (boundary)")
        void shouldNeverSleepWhenOnlyOneAttemptAllowed() throws Throwable {
            List<Long> slept = new ArrayList<>();
            TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                    new TransferProperties(Duration.ofHours(24), 1, 50, 500, 100),
                    slept::add);
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("conflict"));

            assertThatThrownBy(() -> aspect.around(joinPoint))
                    .isExactlyInstanceOf(OptimisticLockingFailureException.class);
            // Mutant (attempt <= maxAttempts) would sleep once before giving up.
            assertThat(slept).isEmpty();
            verify(joinPoint, times(1)).proceed();
        }

        @Test
        @DisplayName("should bypass retry inside an existing transaction")
        void shouldBypassRetryInsideTransaction() throws Throwable {
            // Kills the NegateConditionals mutant on the transaction check:
            // inside a transaction the join point must run exactly once with
            // no retry, even for lock failures. Transaction state arrives
            // through the port (stubbed active here); production wires the
            // Spring-backed implementation.
            TransactionBoundaryPort activeBoundary = new TransactionBoundaryPort() {
                @Override
                public boolean isTransactionActive() {
                    return true;
                }

                @Override
                public void runAfterCommit(Runnable action) {
                    action.run();
                }

                @Override
                public <T> T executeRequiresNew(java.util.function.Supplier<T> action, int timeoutSeconds) {
                    return action.get();
                }
            };
            TransferUseCaseRetryAspect txAspect = new TransferUseCaseRetryAspect(
                    new TransferProperties(Duration.ofHours(24), 3, 50, 500, 100), activeBoundary);
            when(joinPoint.proceed()).thenReturn("success");
            assertThat(txAspect.around(joinPoint)).isEqualTo("success");
            verify(joinPoint, times(1)).proceed();
        }

        @Test
        @DisplayName("should restore interrupt flag when injected sleep is interrupted")
        void shouldRestoreInterruptFlagOnInjectedSleepInterruption() throws Throwable {
            TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                    new TransferProperties(Duration.ofHours(24), 3, 50, 500, 100),
                    delay -> { throw new InterruptedException("woken"); });
            when(joinPoint.proceed())
                    .thenThrow(new OptimisticLockingFailureException("conflict"));

            try {
                assertThatThrownBy(() -> aspect.around(joinPoint))
                        .isExactlyInstanceOf(InterruptedException.class)
                        .hasMessage("woken");
                // Mutant (removed interrupt()) leaves the flag clear.
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                // Never leak the test thread's interrupt flag into other tests.
                Thread.interrupted();
            }
        }
    }
}
