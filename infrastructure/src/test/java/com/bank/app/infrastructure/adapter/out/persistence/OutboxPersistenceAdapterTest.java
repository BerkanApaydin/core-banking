package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import com.bank.app.infrastructure.config.OutboxProperties;
import com.bank.app.infrastructure.config.TransactionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPersistenceAdapterTest {

    @Mock
    private OutboxJpaRepository repository;

    @Captor
    private ArgumentCaptor<OutboxJpaEntity> entityCaptor;

    private OutboxPersistenceAdapter adapter;

    private final LocalDateTime now = LocalDateTime.of(2026, 6, 22, 10, 0);

    @BeforeEach
    void setUp() {
        adapter = new OutboxPersistenceAdapter(repository);
    }

    @Test
    void shouldSaveEventEntry() {
        EventEntry entry = new EventEntry("id-1", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", 0, false, false, null, 0, now, null);

        adapter.save(entry);

        verify(repository).save(entityCaptor.capture());
        OutboxJpaEntity captured = entityCaptor.getValue();
        assertThat(captured.getId()).isEqualTo("id-1");
        assertThat(captured.getAggregateType()).isEqualTo("transfer");
        assertThat(captured.getAggregateId()).isEqualTo("agg-1");
        assertThat(captured.getEventType()).isEqualTo("TransferCompletedEvent");
        assertThat(captured.getPayload()).isEqualTo("{}");
        assertThat(captured.getCreatedAt()).isEqualTo(now);
        assertThat(captured.isProcessed()).isFalse();
        assertThat(captured.getRetryCount()).isZero();
        assertThat(captured.isDeadLetter()).isFalse();
        assertThat(captured.getLastError()).isNull();
        assertThat(captured.getPartition()).isZero();
    }

    @Test
    void shouldFindAndLockUnprocessed() {
        OutboxJpaEntity entity = new OutboxJpaEntity("id-1", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", now, false, 0, false, null, 0);
        // The selection now also filters on the retry backoff
        // (next_attempt_at), so the query takes the "now" reference.
        when(repository.findAndLockUnprocessed(eq(0), any(LocalDateTime.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(entity));

        List<EventEntry> result = adapter.findAndLockUnprocessed(10, 0);

        assertThat(result).hasSize(1);
        EventEntry entry = result.getFirst();
        assertThat(entry.id()).isEqualTo("id-1");
        assertThat(entry.aggregateType()).isEqualTo("transfer");
        assertThat(entry.aggregateId()).isEqualTo("agg-1");
        assertThat(entry.eventType()).isEqualTo("TransferCompletedEvent");
        assertThat(entry.payload()).isEqualTo("{}");
        assertThat(entry.createdAt()).isEqualTo(now);
        assertThat(entry.processed()).isFalse();
        assertThat(entry.retryCount()).isZero();
        assertThat(entry.deadLetter()).isFalse();
        assertThat(entry.lastError()).isNull();
        assertThat(entry.partition()).isZero();
    }

    @Test
    void shouldFindByIdForUpdateSkipLocked() {
        OutboxJpaEntity entity = new OutboxJpaEntity("id-1", "transfer", "agg-1", "TransferCompletedEvent",
                "{}", now, false, 0, false, null, 0);
        when(repository.findByIdForUpdateSkipLocked("id-1")).thenReturn(Optional.of(entity));

        Optional<EventEntry> result = adapter.findByIdForUpdateSkipLocked("id-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("id-1");
    }

    @Test
    void shouldReturnEmptyWhenNotFoundById() {
        when(repository.findByIdForUpdateSkipLocked("id-missing")).thenReturn(Optional.empty());

        Optional<EventEntry> result = adapter.findByIdForUpdateSkipLocked("id-missing");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMarkProcessed() {
        adapter.markProcessed("id-1");

        verify(repository).markProcessed("id-1");
    }

    @Test
    void shouldMarkFailed() {
        adapter.markFailed("id-1", "error", 1);

        // The retry backoff is applied by the statement: base * 2^(retry-1)
        // seconds, capped (V57).
        verify(repository).markFailed("id-1", "error", 1, 5);
    }

    @Test
    void shouldApplyExponentialBackoffOnRepeatedFailures() {
        adapter.markFailed("id-1", "error", 3);

        // retry 3 -> base(5s) * 2^2 = 20s
        verify(repository).markFailed("id-1", "error", 3, 20);
    }

    @Test
    void shouldMarkDeadLetter() {
        adapter.markDeadLetter("id-1", "error", 3);

        verify(repository).markDeadLetter("id-1", "error", 3);
    }

    @Test
    void shouldCountPendingRowsOutsideThePartitionRange() {
        when(repository.countPendingOutsidePartitionRange(4)).thenReturn(7L);

        assertThat(adapter.countPendingOutsidePartitionRange(4)).isEqualTo(7L);
    }

    @Test
    void shouldDeleteProcessedBeforeCutoffInBatches() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 23, 10, 0);
        when(repository.deleteProcessedBatchBefore(eq(cutoff),
                eq(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE)))
                .thenReturn(
                        OutboxPersistenceAdapter.RETENTION_BATCH_SIZE,
                        7);

        int deleted = adapter.deleteProcessedBefore(cutoff);

        assertThat(deleted).isEqualTo(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE + 7);
        verify(repository, times(2)).deleteProcessedBatchBefore(eq(cutoff),
                eq(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE));
    }

    @Test
    void shouldCapRetentionBatchesPerRun() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 23, 10, 0);
        when(repository.deleteProcessedBatchBefore(eq(cutoff),
                eq(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE)))
                .thenReturn(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE);

        int deleted = adapter.deleteProcessedBefore(cutoff);

        assertThat(deleted).isEqualTo(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE
                * OutboxPersistenceAdapter.MAX_BATCHES_PER_RUN);
        verify(repository, times(OutboxPersistenceAdapter.MAX_BATCHES_PER_RUN))
                .deleteProcessedBatchBefore(eq(cutoff),
                        eq(OutboxPersistenceAdapter.RETENTION_BATCH_SIZE));
    }

    @Test
    void shouldRunRetentionBatchInConfiguredRequiresNewTransaction() {
        var txManager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var transactionalAdapter = new OutboxPersistenceAdapter(repository,
                new OutboxProperties(5, 50, 2, 2000L, 30, 4, 12, 30L), null,
                txManager, new TransactionProperties(17));
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 23, 10, 0);
        when(repository.deleteProcessedBatchBefore(cutoff, 1000)).thenReturn(3);

        assertThat(transactionalAdapter.deleteProcessedBefore(cutoff)).isEqualTo(3);

        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(txManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(definition.getValue().getTimeout()).isEqualTo(17);
        verify(txManager).commit(status);
    }
}
