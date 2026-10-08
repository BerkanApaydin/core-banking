package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.IdempotencyReaperProperties;
import com.bank.app.infrastructure.adapter.out.scheduling.AdvisorySchedulerLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyPendingReaper")
class IdempotencyPendingReaperTest {

    @Mock
    private IdempotencyPort idempotencyPort;
    @Mock
    private ClockProviderPort clockProvider;

    private final Clock fixedClock = Clock.systemUTC();
    private SimpleMeterRegistry meters;
    private IdempotencyPendingReaper reaper;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(clockProvider.clock()).thenReturn(fixedClock);
        meters = new SimpleMeterRegistry();
        reaper = new IdempotencyPendingReaper(
                idempotencyPort,
                new IdempotencyReaperProperties(true, Duration.ofMinutes(15)),
                AdvisorySchedulerLock.alwaysRun(),
                clockProvider,
                meters);
    }

    @Nested
    @DisplayName("reapStalePending")
    class Reap {

        @Test
        @DisplayName("transitions stale PENDING rows to FAILED and counts them")
        void shouldReapStalePending() {
            when(idempotencyPort.failStalePending(any(LocalDateTime.class))).thenReturn(3);

            reaper.reapStalePending();

            verify(idempotencyPort).failStalePending(any(LocalDateTime.class));
            assertThat(meters.counter(IdempotencyPendingReaper.REAPED_COUNTER).count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("does nothing when no stale rows exist")
        void shouldDoNothingWhenEmpty() {
            when(idempotencyPort.failStalePending(any(LocalDateTime.class))).thenReturn(0);

            reaper.reapStalePending();

            verify(idempotencyPort).failStalePending(any(LocalDateTime.class));
            assertThat(meters.counter(IdempotencyPendingReaper.REAPED_COUNTER).count()).isZero();
        }

        @Test
        @DisplayName("survives a store failure without killing the scheduler")
        void shouldSurviveStoreFailure() {
            when(idempotencyPort.failStalePending(any(LocalDateTime.class)))
                    .thenThrow(new IllegalStateException("db down"));

            assertThatCode(reaper::reapStalePending).doesNotThrowAnyException();

            verify(idempotencyPort).failStalePending(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("skips work when another replica holds the lock")
        void shouldSkipWhenLockContended() {
            AdvisorySchedulerLock contended = org.mockito.Mockito.mock(AdvisorySchedulerLock.class);
            when(contended.runIfLeader(
                    org.mockito.ArgumentMatchers.eq(IdempotencyPendingReaper.LOCK_NAME),
                    any(Runnable.class))).thenReturn(false);
            IdempotencyPendingReaper follower = new IdempotencyPendingReaper(
                    idempotencyPort,
                    new IdempotencyReaperProperties(true, Duration.ofMinutes(15)),
                    contended,
                    clockProvider,
                    meters);

            follower.reapStalePending();

            verify(idempotencyPort, never()).failStalePending(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("properties")
    class Properties {

        @Test
        @DisplayName("rejects non-positive thresholds")
        void shouldValidate() {
            assertThatCode(() -> new IdempotencyReaperProperties(true, Duration.ZERO))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new IdempotencyReaperProperties(true, Duration.ofMinutes(-1)))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
