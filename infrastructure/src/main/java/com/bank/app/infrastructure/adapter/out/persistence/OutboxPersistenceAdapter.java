package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.OutboxPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxJpaRepository repository;

    public OutboxPersistenceAdapter(OutboxJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(EventEntry entry) {
        repository.save(new OutboxJpaEntity(
                entry.id(), entry.aggregateType(), entry.aggregateId(),
                entry.eventType(), entry.payload(), entry.createdAt(),
                entry.processed(), entry.retryCount(), entry.deadLetter(),
                entry.lastError(), entry.partition()
        ));
    }

    @Override
    @Transactional
    public List<EventEntry> findAndLockUnprocessed(int limit, int partition) {
        return repository.findAndLockUnprocessed(partition, PageRequest.of(0, limit))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Optional<EventEntry> findByIdForUpdateSkipLocked(String id) {
        return repository.findByIdForUpdateSkipLocked(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public void markProcessed(String id) {
        repository.markProcessed(id);
    }

    @Override
    @Transactional
    public void markFailed(String id, String error, int retryCount) {
        repository.markFailed(id, error, retryCount);
    }

    @Override
    @Transactional
    public void markDeadLetter(String id, String error, int retryCount) {
        repository.markDeadLetter(id, error, retryCount);
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
