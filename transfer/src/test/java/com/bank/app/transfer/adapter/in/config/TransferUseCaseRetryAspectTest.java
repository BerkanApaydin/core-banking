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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
@DisplayName("TransferUseCaseRetryAspect")
class TransferUseCaseRetryAspectTest {

    private final TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
            new TransferProperties(24, 3, 500, 2000));

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
    }
}
