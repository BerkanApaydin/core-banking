package com.bank.app.transfer.adapter.in.aspect;

import com.bank.app.transfer.config.TransferProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backoff configuration of the retry aspect.
 *
 * <p>{@code ExponentialBackoffPolicy} requires {@code maxDelayMs >= initialDelayMs}
 * (ExponentialBackoffPolicy.java:19). The aspect builds the policy on EVERY
 * invocation (TransferUseCaseRetryAspect.java:101-104) and is the outermost
 * proxy around the place/cancel use cases, so an inverted delay pair used to
 * turn every money request into a 500 before any money was touched. The pair
 * is now validated at configuration time, so the invalid object cannot even be
 * constructed.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferUseCaseRetryAspect backoff configuration")
class TransferUseCaseRetryAspectBackoffConfigTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Test
    @DisplayName("should reject an inverted delay pair at configuration time, before any request")
    void shouldRejectInvertedDelayPairAtConfigurationTime() {
        assertThatThrownBy(() -> new TransferProperties(Duration.ofHours(24), 3, 500, 100, 100))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDelayMs");
    }

    @Test
    @DisplayName("should accept a delay pair whose maximum equals the initial delay")
    void shouldAcceptMaxDelayEqualToInitialDelay() throws Throwable {
        TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                new TransferProperties(Duration.ofHours(24), 3, 500, 500, 100));
        when(joinPoint.proceed()).thenReturn("success");

        assertThat(aspect.around(joinPoint)).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("should accept a delay pair whose maximum exceeds the initial delay")
    void shouldAcceptMaxDelayAboveInitialDelay() throws Throwable {
        TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                new TransferProperties(Duration.ofHours(24), 3, 500, 2000, 100));
        when(joinPoint.proceed()).thenReturn("success");

        assertThat(aspect.around(joinPoint)).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("should not build the backoff policy inside an already active transaction")
    void shouldNotBuildBackoffPolicyInsideActiveTransaction() throws Throwable {
        // The policy is only built when no transaction is active.
        com.bank.app.common.application.port.out.TransactionBoundaryPort activeBoundary =
                mock(com.bank.app.common.application.port.out.TransactionBoundaryPort.class);
        when(activeBoundary.isTransactionActive()).thenReturn(true);

        TransferUseCaseRetryAspect aspect = new TransferUseCaseRetryAspect(
                new TransferProperties(Duration.ofHours(24), 3, 500, 2000, 100), activeBoundary);
        when(joinPoint.proceed()).thenReturn("success");

        assertThatCode(() -> assertThat(aspect.around(joinPoint)).isEqualTo("success"))
                .doesNotThrowAnyException();
        verify(joinPoint).proceed();
    }
}
