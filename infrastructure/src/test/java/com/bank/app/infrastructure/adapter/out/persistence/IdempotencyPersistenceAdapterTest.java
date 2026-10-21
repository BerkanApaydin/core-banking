package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class IdempotencyPersistenceAdapterTest {

    @Mock
    private IdempotencyKeyJpaRepository repository;

    private IdempotencyPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new IdempotencyPersistenceAdapter(repository);
    }

    @Test
    void shouldFindById() {
        var now = LocalDateTime.now();
        var entity = new IdempotencyKeyJpaEntity("key1", "COMPLETED", "response", 200, now);
        when(repository.findById("key1")).thenReturn(Optional.of(entity));

        Optional<Entry> result = adapter.findById("key1");

        assertThat(result).isPresent();
        assertThat(result.get().key()).isEqualTo("key1");
        assertThat(result.get().status()).isEqualTo("COMPLETED");
        assertThat(result.get().responseBody()).isEqualTo("response");
        assertThat(result.get().responseStatus()).isEqualTo(200);
        assertThat(result.get().replayable()).isTrue();
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        Optional<Entry> result = adapter.findById("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldTryCreateSuccessfully() {
        var now = LocalDateTime.now();
        when(repository.tryInsert("key1", null, now, "HTTP")).thenReturn(1);

        boolean result = adapter.tryCreate("key1", now);

        assertThat(result).isTrue();
        verify(repository).tryInsert("key1", null, now, "HTTP");
    }

    @Test
    void shouldReturnFalseWhenTryCreateFailsWithDuplicate() {
        var now = LocalDateTime.now();
        when(repository.tryInsert("key1", null, now, "HTTP")).thenReturn(0);

        boolean result = adapter.tryCreate("key1", now);

        assertThat(result).isFalse();
    }

    @Test
    void shouldMapNonReplayableEntry() {
        var now = LocalDateTime.now();
        var entity = new IdempotencyKeyJpaEntity("key1", "COMPLETED", null, 200, now);
        entity.setResponseReplayable(false);
        when(repository.findById("key1")).thenReturn(Optional.of(entity));

        Optional<Entry> result = adapter.findById("key1");

        assertThat(result).isPresent();
        assertThat(result.get().responseBody()).isNull();
        assertThat(result.get().replayable()).isFalse();
    }

    @Test
    void shouldOnlyClaimFailedKeyWhenConditionalUpdateWins() {
        var now = LocalDateTime.now();
        when(repository.resetFailed("key1", null, now)).thenReturn(1);

        assertThat(adapter.tryResetFailed("key1", now)).isTrue();
        verify(repository).resetFailed("key1", null, now);
    }

    @Test
    void shouldMarkCompleted() {
        when(repository.completeIfPending("key1", "response", 200, true)).thenReturn(1);

        adapter.markCompleted("key1", "response", 200);

        verify(repository).completeIfPending("key1", "response", 200, true);
    }

    @Test
    void shouldTreatReplayAsIdempotentWhenAlreadyCompleted() {
        var entity = new IdempotencyKeyJpaEntity("key1", "COMPLETED", "old", 200, LocalDateTime.now());
        when(repository.completeIfPending("key1", "response", 200, true)).thenReturn(0);
        when(repository.findById("key1")).thenReturn(Optional.of(entity));

        adapter.markCompleted("key1", "response", 200);

        verify(repository).completeIfPending("key1", "response", 200, true);
    }

    @Test
    void shouldRejectCompletionWhenReservationIsMissing() {
        when(repository.completeIfPending("missing", "response", 200, true)).thenReturn(0);
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> adapter.markCompleted("missing", "response", 200));

        verify(repository).completeIfPending("missing", "response", 200, true);
    }

    @Test
    void shouldWarnWhenCompletionLosesRaceWithPendingReaper() {
        var entity = new IdempotencyKeyJpaEntity("key1", "FAILED", null, 200, LocalDateTime.now());
        when(repository.completeIfPending("key1", "response", 200, true)).thenReturn(0);
        when(repository.findById("key1")).thenReturn(Optional.of(entity));
        Logger adapterLogger = (Logger) LoggerFactory.getLogger(IdempotencyPersistenceAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        adapterLogger.addAppender(appender);
        try {
            adapter.markCompleted("key1", "response", 200);
        } finally {
            adapterLogger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("key=key1")
                    .doesNotContain("response");
        });
    }

    @Test
    void shouldMarkFailed() {
        when(repository.failIfPending("key1")).thenReturn(1);

        adapter.markFailed("key1");

        verify(repository).failIfPending("key1");
    }

    @Test
    void shouldMarkFailedWhenEntityNotFound() {
        when(repository.failIfPending("missing")).thenReturn(0);

        adapter.markFailed("missing");

        verify(repository).failIfPending("missing");
    }

    @Test
    void shouldDeleteExpired() {
        var threshold = LocalDateTime.now();
        when(repository.deleteExpiredTerminalRequestsBatch(threshold, 1000)).thenReturn(3);

        int deleted = adapter.deleteExpired(threshold);

        assertThat(deleted).isEqualTo(3);
        verify(repository).deleteExpiredTerminalRequestsBatch(threshold, 1000);
    }

    @Test
    void shouldDeleteExpiredHandlerKeys() {
        var threshold = LocalDateTime.now();
        when(repository.deleteExpiredHandlerKeysBatch(threshold, 1000)).thenReturn(4);

        int deleted = adapter.deleteExpiredHandlerKeys(threshold);

        assertThat(deleted).isEqualTo(4);
    }

    @Test
    void shouldPageBatchesUntilShortBatch() {
        // P-3: a full batch means "maybe more" — the loop continues; a short
        // batch stops it. 1000 + 3 = 1003 total over two round trips.
        var threshold = LocalDateTime.now();
        when(repository.deleteExpiredTerminalRequestsBatch(threshold, 1000)).thenReturn(1000, 3);

        int deleted = adapter.deleteExpired(threshold);

        assertThat(deleted).isEqualTo(1003);
        verify(repository, times(2)).deleteExpiredTerminalRequestsBatch(threshold, 1000);
    }

    @Test
    void shouldStopAtTheSymmetricBatchBudgetForRequestKeys() {
        var threshold = LocalDateTime.now();
        when(repository.deleteExpiredTerminalRequestsBatch(threshold, 1000)).thenReturn(1000);

        int deleted = adapter.deleteExpired(threshold);

        assertThat(deleted)
                .isEqualTo(1000 * IdempotencyPersistenceAdapter.MAX_BATCHES_PER_RUN);
        verify(repository, times(IdempotencyPersistenceAdapter.MAX_BATCHES_PER_RUN))
                .deleteExpiredTerminalRequestsBatch(threshold, 1000);
    }

    @Test
    void shouldStopAtTheSymmetricBatchBudgetForHandlerKeys() {
        var threshold = LocalDateTime.now();
        when(repository.deleteExpiredHandlerKeysBatch(threshold, 1000)).thenReturn(1000);

        int deleted = adapter.deleteExpiredHandlerKeys(threshold);

        assertThat(deleted)
                .isEqualTo(1000 * IdempotencyPersistenceAdapter.MAX_BATCHES_PER_RUN);
        verify(repository, times(IdempotencyPersistenceAdapter.MAX_BATCHES_PER_RUN))
                .deleteExpiredHandlerKeysBatch(threshold, 1000);
    }

    @Test
    void shouldMarkOversizedReplayBodyNotReplayableInsteadOfStoringCorruptJson() {
        // R-I06: completion joins the business tx, so an oversized replay body
        // must still not fail the write. It is no longer truncated (a cut JSON
        // fragment is corrupt on replay); it is stored as NULL and flagged
        // non-replayable so the codec can return an explicit problem instead.
        String oversized = "x".repeat(IdempotencyPersistenceAdapter.MAX_RESPONSE_BODY_CHARS + 1);
        when(repository.completeIfPending("key1", null, 200, false)).thenReturn(1);

        adapter.markCompleted("key1", oversized, 200);

        verify(repository).completeIfPending("key1", null, 200, false);
    }

    @Test
    void shouldKeepBodyAtExactReplayCapReplayable() {
        String atCap = "x".repeat(IdempotencyPersistenceAdapter.MAX_RESPONSE_BODY_CHARS);
        when(repository.completeIfPending("key1", atCap, 200, true)).thenReturn(1);

        adapter.markCompleted("key1", atCap, 200);

        verify(repository).completeIfPending("key1", atCap, 200, true);
    }

    @Test
    void shouldFailStalePending() {
        var threshold = LocalDateTime.now();
        when(repository.failStalePending(threshold)).thenReturn(2);

        int reaped = adapter.failStalePending(threshold);

        assertThat(reaped).isEqualTo(2);
        verify(repository).failStalePending(threshold);
    }
}
