package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.infrastructure.adapter.in.config.OutboxProperties;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate retentionTransaction;

    @Autowired
    public OutboxPersistenceAdapter(OutboxJpaRepository repository, OutboxProperties properties,
            ClockProviderPort clockProvider, PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties) {
        this(repository, properties, clockProvider,
                retentionTransaction(transactionManager, transactionProperties));
    }

    public OutboxPersistenceAdapter(OutboxJpaRepository repository, OutboxProperties properties,
            ClockProviderPort clockProvider) {
        this(repository, properties, clockProvider, null);
    }

    private OutboxPersistenceAdapter(OutboxJpaRepository repository, OutboxProperties properties,
            ClockProviderPort clockProvider, TransactionTemplate retentionTransaction) {
        this.repository = repository;
        this.clockProvider = clockProvider != null ? clockProvider : Clock::systemUTC;
        this.backoffBaseSeconds = properties.retryBackoffBaseSeconds();
        this.backoffMaxSeconds = properties.retryBackoffMaxSeconds();
        this.retentionTransaction = retentionTransaction;
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
        // Cap the exponent before multiplying: 2^exponent can overflow for a
        // large retry count, while any capped value still saturates at max.
        long delay = (long) base * (1L << Math.min(exponent, 31));
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
    public int deleteProcessedBefore(LocalDateTime cutoff) {
        // Each repository batch commits in its own REQUIRES_NEW transaction
        // (see retentionTransaction below): a failure or timeout in one batch
        // must not roll back the batches that already completed, and the
        // advisory lock transaction stays short instead of owning the whole run.
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = deleteBatch(cutoff);
            total += deleted;
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private int deleteBatch(LocalDateTime cutoff) {
        if (retentionTransaction == null) {
            return repository.deleteProcessedBatchBefore(cutoff, RETENTION_BATCH_SIZE);
        }
        Integer deleted = retentionTransaction.execute(
                status -> repository.deleteProcessedBatchBefore(cutoff, RETENTION_BATCH_SIZE));
        return deleted != null ? deleted : 0;
    }

    private static TransactionTemplate retentionTransaction(PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(transactionProperties.timeoutSeconds());
        return template;
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
