package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        when(repository.tryInsert("key1", null, now)).thenReturn(1);

        boolean result = adapter.tryCreate("key1", now);

        assertThat(result).isTrue();
        verify(repository).tryInsert("key1", null, now);
    }

    @Test
    void shouldReturnFalseWhenTryCreateFailsWithDuplicate() {
        var now = LocalDateTime.now();
        when(repository.tryInsert("key1", null, now)).thenReturn(0);

        boolean result = adapter.tryCreate("key1", now);

        assertThat(result).isFalse();
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
        when(repository.completeIfPending("key1", "response", 200)).thenReturn(1);

        adapter.markCompleted("key1", "response", 200);

        verify(repository).completeIfPending("key1", "response", 200);
    }

    @Test
    void shouldTreatReplayAsIdempotentWhenAlreadyCompleted() {
        var entity = new IdempotencyKeyJpaEntity("key1", "COMPLETED", "old", 200, LocalDateTime.now());
        when(repository.completeIfPending("key1", "response", 200)).thenReturn(0);
        when(repository.findById("key1")).thenReturn(Optional.of(entity));

        adapter.markCompleted("key1", "response", 200);

        verify(repository).completeIfPending("key1", "response", 200);
    }

    @Test
    void shouldRejectCompletionWhenReservationIsMissing() {
        when(repository.completeIfPending("missing", "response", 200)).thenReturn(0);
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> adapter.markCompleted("missing", "response", 200));

        verify(repository).completeIfPending("missing", "response", 200);
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
    void shouldTruncateOversizedReplayBodyInsteadOfFailingBusinessWrite() {
        // L6: completion joins the business tx; an oversized replay body must
        // truncate (replay-only) rather than fail the write and roll money back.
        String oversized = "x".repeat(IdempotencyPersistenceAdapter.MAX_RESPONSE_BODY_CHARS + 1);
        when(repository.completeIfPending(eq("key1"), anyString(), eq(200))).thenReturn(1);

        adapter.markCompleted("key1", oversized, 200);

        verify(repository).completeIfPending(eq("key1"),
                argThat(body -> body != null
                        && body.length() == IdempotencyPersistenceAdapter.MAX_RESPONSE_BODY_CHARS),
                eq(200));
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
