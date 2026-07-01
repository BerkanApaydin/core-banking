package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.OutboxPort.EventEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

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
        when(repository.findAndLockUnprocessed(0, PageRequest.of(0, 10)))
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
        when(repository.findByIdForUpdate("id-1")).thenReturn(Optional.of(entity));

        Optional<EventEntry> result = adapter.findByIdForUpdateSkipLocked("id-1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("id-1");
    }

    @Test
    void shouldReturnEmptyWhenNotFoundById() {
        when(repository.findByIdForUpdate("id-missing")).thenReturn(Optional.empty());

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

        verify(repository).markFailed("id-1", "error", 1);
    }

    @Test
    void shouldMarkDeadLetter() {
        adapter.markDeadLetter("id-1", "error", 3);

        verify(repository).markDeadLetter("id-1", "error", 3);
    }
}
