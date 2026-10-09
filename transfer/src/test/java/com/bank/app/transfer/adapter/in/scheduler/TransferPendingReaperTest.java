package com.bank.app.transfer.adapter.in.scheduler;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.AuditEvent;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.config.TransferReaperProperties;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.bank.app.common.domain.AccountId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferPendingReaper")
class TransferPendingReaperTest {

    @Mock
    private LoadTransferPort loadTransferPort;
    @Mock
    private SaveTransferPort saveTransferPort;
    @Mock
    private AuditEventPort auditEventPort;
    @Mock
    private ClockProviderPort clockProvider;

    private final Clock fixedClock = Clock.systemUTC();
    private TransferPendingReaper reaper;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        lenient().when(clockProvider.clock()).thenReturn(fixedClock);
        meters = new SimpleMeterRegistry();
        reaper = new TransferPendingReaper(loadTransferPort, saveTransferPort, auditEventPort,
                new TransferReaperProperties(true, Duration.ofMinutes(15), 50, 30), clockProvider, meters);
    }

    private static Transfer pending(Long id) {
        return new Transfer(id, new AccountId(10L), new AccountId(20L), Money.exact(new BigDecimal("100.00"), Currency.TRY),
                TransferStatus.PENDING, LocalDateTime.now(Clock.systemUTC()));
    }

    @Nested
    @DisplayName("reap")
    class Reap {

        @Test
        @DisplayName("marks stale PENDING rows FAILED and audits each one")
        void shouldReapStalePending() {
            Transfer stale = pending(1L);
            when(loadTransferPort.findStalePending(any(), anyInt())).thenReturn(List.of(stale));
            when(loadTransferPort.findByIdForUpdate(1L)).thenReturn(Optional.of(pending(1L)));

            reaper.reap();

            verify(saveTransferPort).save(any(Transfer.class));
            verify(auditEventPort).publish(any(AuditEvent.class));
            assertThat(meters.counter(TransferPendingReaper.REAPED_COUNTER).count()).isOne();
        }

        @Test
        @DisplayName("does nothing when no stale rows exist")
        void shouldDoNothingWhenEmpty() {
            when(loadTransferPort.findStalePending(any(), anyInt())).thenReturn(List.of());

            reaper.reap();

            verify(saveTransferPort, never()).save(any());
            verify(auditEventPort, never()).publish(any());
        }

        @Test
        @DisplayName("treats a concurrently completed row as conflict, not failure")
        void shouldCountConflictWhenAlreadyCompleted() {
            Transfer stale = pending(2L);
            Transfer completed = new Transfer(2L, new AccountId(10L), new AccountId(20L),
                    Money.exact(new BigDecimal("100.00"), Currency.TRY),
                    TransferStatus.COMPLETED, LocalDateTime.now(Clock.systemUTC()));
            when(loadTransferPort.findStalePending(any(), anyInt())).thenReturn(List.of(stale));
            when(loadTransferPort.findByIdForUpdate(2L)).thenReturn(Optional.of(completed));

            reaper.reap();

            verify(saveTransferPort, never()).save(any());
            verify(auditEventPort, never()).publish(any());
            assertThat(meters.counter(TransferPendingReaper.CONFLICT_COUNTER).count()).isOne();
        }

        @Test
        @DisplayName("treats a version conflict on save as conflict and continues the batch")
        void shouldCountConflictOnOptimisticLock() {
            Transfer first = pending(3L);
            Transfer second = pending(4L);
            when(loadTransferPort.findStalePending(any(), anyInt())).thenReturn(List.of(first, second));
            when(loadTransferPort.findByIdForUpdate(3L)).thenReturn(Optional.of(pending(3L)));
            when(loadTransferPort.findByIdForUpdate(4L)).thenReturn(Optional.of(pending(4L)));
            when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
                Transfer t = invocation.getArgument(0);
                if (t.getId() == 3L) {
                    throw new ObjectOptimisticLockingFailureException("transfers", 3L);
                }
                return t;
            });

            reaper.reap();

            assertThat(meters.counter(TransferPendingReaper.CONFLICT_COUNTER).count()).isOne();
            assertThat(meters.counter(TransferPendingReaper.REAPED_COUNTER).count()).isOne();
            verify(auditEventPort).publish(any(AuditEvent.class));
        }

        @Test
        @DisplayName("skips rows that vanished and survives a poisoned row")
        void shouldSurviveMissingAndPoisonedRows() {
            Transfer missing = pending(5L);
            Transfer poisoned = pending(6L);
            Transfer healthy = pending(7L);
            when(loadTransferPort.findStalePending(any(), anyInt()))
                    .thenReturn(List.of(missing, poisoned, healthy));
            when(loadTransferPort.findByIdForUpdate(5L)).thenReturn(Optional.empty());
            when(loadTransferPort.findByIdForUpdate(6L)).thenThrow(new IllegalStateException("db hiccup"));
            when(loadTransferPort.findByIdForUpdate(7L)).thenReturn(Optional.of(pending(7L)));

            assertThatCode(reaper::reap).doesNotThrowAnyException();

            verify(saveTransferPort).save(any(Transfer.class));
            assertThat(meters.counter(TransferPendingReaper.REAPED_COUNTER).count()).isOne();
        }

        @Test
        @DisplayName("survives a failed scan without killing the scheduler")
        void shouldSurviveScanFailure() {
            when(loadTransferPort.findStalePending(any(), anyInt()))
                    .thenThrow(new IllegalStateException("db down"));

            assertThatCode(reaper::reap).doesNotThrowAnyException();

            verify(saveTransferPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("properties")
    class Properties {

        @Test
        @DisplayName("rejects non-positive thresholds and out-of-range batches")
        void shouldValidate() {
            assertThatCode(() -> new TransferReaperProperties(true, Duration.ZERO, 50, 30))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new TransferReaperProperties(true, Duration.ofMinutes(15), 0, 30))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new TransferReaperProperties(true, Duration.ofMinutes(15), 201, 30))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new TransferReaperProperties(true, Duration.ofMinutes(15), 50, 0))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatCode(() -> new TransferReaperProperties(true, Duration.ofMinutes(15), 50, 301))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
