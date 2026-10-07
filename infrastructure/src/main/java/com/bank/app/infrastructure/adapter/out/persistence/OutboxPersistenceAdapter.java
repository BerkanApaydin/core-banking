package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.OutboxPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxJpaRepository repository;

    public OutboxPersistenceAdapter(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(timeout = 30)
    public void save(EventEntry entry) {
        repository.save(new OutboxJpaEntity(
                entry.id(), entry.aggregateType(), entry.aggregateId(),
                entry.eventType(), entry.payload(), entry.createdAt(),
                entry.processed(), entry.retryCount(), entry.deadLetter(),
                entry.lastError(), entry.partition()
        ));
    }

    @Override
    @Transactional(timeout = 30)
    public List<EventEntry> findAndLockUnprocessed(int limit, int partition) {
        return repository.findAndLockUnprocessed(partition, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingOutsidePartitionRange(int partitionCount) {
        return repository.countPendingOutsidePartitionRange(partitionCount);
    }

    @Override
    @Transactional(timeout = 30)
    public Optional<EventEntry> findByIdForUpdateSkipLocked(String id) {
        return repository.findByIdForUpdateSkipLocked(id).map(this::toDomain);
    }

    @Override
    @Transactional(timeout = 30)
    public void markProcessed(String id) {
        repository.markProcessed(id);
    }

    @Override
    @Transactional(timeout = 30)
    public void markFailed(String id, String error, int retryCount) {
        repository.markFailed(id, error, retryCount);
    }

    @Override
    @Transactional(timeout = 30)
    public void markDeadLetter(String id, String error, int retryCount) {
        repository.markDeadLetter(id, error, retryCount);
    }

    @Override
    @Transactional(timeout = 30)
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        return repository.deleteProcessedBefore(cutoff);
    }

    private EventEntry toDomain(OutboxJpaEntity entity) {
        return new EventEntry(
                entity.getId(), entity.getAggregateType(), entity.getAggregateId(),
                entity.getEventType(), entity.getPayload(), entity.getRetryCount(),
                entity.isProcessed(), entity.isDeadLetter(), entity.getLastError(),
                entity.getPartition(), entity.getCreatedAt(), entity.getProcessedAt()
        );
    }
}
