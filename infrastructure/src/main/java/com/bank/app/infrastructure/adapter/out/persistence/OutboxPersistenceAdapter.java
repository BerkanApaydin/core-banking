package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OutboxPersistenceAdapter implements OutboxPort {

    private final OutboxJpaRepository repository;
    private final ClockProviderPort clockProvider;
    private final int backoffBaseSeconds;
    private final int backoffMaxSeconds;

    @Autowired
    public OutboxPersistenceAdapter(OutboxJpaRepository repository, OutboxProperties properties,
            ClockProviderPort clockProvider) {
        this.repository = repository;
        this.clockProvider = clockProvider != null ? clockProvider : Clock::systemUTC;
        this.backoffBaseSeconds = properties.retryBackoffBaseSeconds();
        this.backoffMaxSeconds = properties.retryBackoffMaxSeconds();
    }

    /**
     * Convenience constructor for callers that do not tune the retry backoff
     * and use the system UTC clock (the time-strategy rule bans a bare
     * {@code LocalDateTime.now()} in main code — see
     * {@code CodingRulesArchitectureTest}).
     */
    public OutboxPersistenceAdapter(OutboxJpaRepository repository) {
        this(repository, new OutboxProperties(5, 50, 2, 2000L, 30, 4, 12, 30L), null);
    }

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public void save(EventEntry entry) {
        repository.save(new OutboxJpaEntity(
                entry.id(), entry.aggregateType(), entry.aggregateId(),
                entry.eventType(), entry.payload(), entry.createdAt(),
                entry.processed(), entry.retryCount(), entry.deadLetter(),
                entry.lastError(), entry.partition()
        ));
    }

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public List<EventEntry> findAndLockUnprocessed(int limit, int partition) {
        return repository.findAndLockUnprocessed(partition, LocalDateTime.now(clockProvider.clock()),
                        PageRequest.of(0, limit))
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
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public Optional<EventEntry> findByIdForUpdateSkipLocked(String id) {
        return repository.findByIdForUpdateSkipLocked(id).map(this::toDomain);
    }

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public void markProcessed(String id) {
        repository.markProcessed(id);
    }

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public void markFailed(String id, String error, int retryCount) {
        repository.markFailed(id, error, retryCount, backoffSeconds(retryCount));
    }

    /**
     * Exponential backoff for the next attempt (V57).
     *
     * <p>The delay is computed here — one place — and applied by the SQL
     * statement with the database clock. Without it every failed event was
     * retried on the next poll cycle (2s by default), so a poison event
     * burned all five retries in about ten seconds and dead-lettered long
     * before a transient condition could clear.
     */
    private int backoffSeconds(int retryCount) {
        int base = Math.max(1, backoffBaseSeconds);
        int max = Math.max(base, backoffMaxSeconds);
        int exponent = Math.max(0, retryCount - 1);
        // Shift with a cap: 2^exponent can overflow for a large retry count.
        long delay = exponent >= 31 ? max : (long) base << exponent;
        return (int) Math.min(delay, max);
    }

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public void markDeadLetter(String id, String error, int retryCount) {
        repository.markDeadLetter(id, error, retryCount);
    }

    /** Rows per retention batch; small enough to stay far under the 30s scheduler-lock budget. */
    static final int RETENTION_BATCH_SIZE = 1000;
    /** Batches per schedule: larger backlogs drain over consecutive schedules instead of timing out. */
    static final int MAX_BATCHES_PER_RUN = 10;

    @Override
    @Transactional(timeoutString = "${app.transaction.timeout-seconds:30}")
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = repository.deleteProcessedBatchBefore(cutoff, RETENTION_BATCH_SIZE);
            total += deleted;
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
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
