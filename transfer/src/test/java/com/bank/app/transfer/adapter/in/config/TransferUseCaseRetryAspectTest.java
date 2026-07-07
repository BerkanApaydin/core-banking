package com.bank.app.transfer.adapter.in.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("TransferUseCaseRetryAspect")
class TransferUseCaseRetryAspectTest {

    private final TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
            new TransferProperties(24, 3, 50, 500));

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
        @DisplayName("should throw OptimisticLockingFailureException after exhausting retries")
        void shouldThrowAfterExhaustingRetries() throws Throwable {
            OptimisticLockingFailureException original = new OptimisticLockingFailureException("persistent conflict");
            when(joinPoint.proceed()).thenThrow(original);

            assertThatThrownBy(() -> aspect.around(joinPoint))
                    .isExactlyInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessage("persistent conflict");
            verify(joinPoint, org.mockito.Mockito.times(3)).proceed();
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
        @DisplayName("should throw original exception after single attempt when maxAttempts is 1")
        void shouldThrowAfterSingleAttemptWhenMaxAttemptsIsOne() throws Throwable {
            // With maxAttempts=1 and a failure, original code runs the loop once and throws OLFE.
            // L35 mutant (attempt <= maxAttempts → attempt < maxAttempts) skips the loop entirely,
            // leaving lastException=null and throwing IllegalStateException instead.
            TransferUseCaseRetryAspect aspect1 = new TransferUseCaseRetryAspect(
                    new TransferProperties(24, 1, 100, 1000));
            OptimisticLockingFailureException original = new OptimisticLockingFailureException("conflict");
            when(joinPoint.proceed()).thenThrow(original);

            assertThatThrownBy(() -> aspect1.around(joinPoint))
                    .isExactlyInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessage("conflict");
            verify(joinPoint, org.mockito.Mockito.times(1)).proceed();
        }

        @Test
        @DisplayName("should double delay after each retry")
        void shouldDoubleDelayAfterEachRetry() throws Throwable {
            // 2 failures then success. Timing detects delay-related mutations:
            // Original: sleep(1000) + sleep(2000) = ~3000ms
            // L42 delay/2: sleep(1000) + sleep(500) = ~1500ms (< 2000 assertion fails)
            // L41 removed sleep: ~0ms (< 2000 assertion fails)
            TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                    new TransferProperties(24, 3, 1000, 10000));
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
            verify(joinPoint, org.mockito.Mockito.times(3)).proceed();
        }
    }
}
