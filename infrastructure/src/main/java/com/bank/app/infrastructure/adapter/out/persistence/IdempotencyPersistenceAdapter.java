package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.infrastructure.adapter.in.config.TransactionProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.IntSupplier;

@Repository
public class IdempotencyPersistenceAdapter implements IdempotencyPort {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPersistenceAdapter.class);

    /**
     * L6/R-I06: replay-body application cap. The column is unbounded TEXT
     * since V53, so an oversized body can never roll the business transaction
     * back. Cutting the JSON at this boundary would return corrupt JSON on a
     * replay, so bodies above the cap are not stored at all: the completion is
     * marked {@code replayable = false} and the replay path returns an
     * explicit problem instead of a truncated business payload.
     */
    static final int MAX_RESPONSE_BODY_CHARS = 10_000;

    /**
     * The {@code key_kind} discriminator (V38). Declared by the CALLER, not
     * derived from the key text: the kind drives the retention split, so a
     * misclassified key is deleted by the wrong sweep.
     */
    static final String KEY_KIND_HTTP = "HTTP";
    static final String KEY_KIND_HANDLER = "HANDLER";

    private final IdempotencyKeyJpaRepository repository;

    private final TransactionTemplate retentionTransaction;

    @Autowired
    public IdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository repository,
            PlatformTransactionManager transactionManager, TransactionProperties transactionProperties) {
        this.repository = repository;
        this.retentionTransaction = retentionTransaction(transactionManager, transactionProperties);
    }

    public IdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
        this.retentionTransaction = null;
    }

    @Override
    public Optional<Entry> findById(@NonNull String key) {
        return repository.findById(key)
                .map(e -> new Entry(e.getKey(), e.getStatus(), e.getResponseBody(), e.getResponseStatus(),
                        e.getCreatedAt(), e.getRequestHash(), e.isResponseReplayable()));
    }

    @Override
    public boolean tryCreate(String key, LocalDateTime now) {
        return tryCreate(key, null, now);
    }

    @Override
    public boolean tryCreate(String key, String requestHash, LocalDateTime now) {
        return repository.tryInsert(key, requestHash, now, KEY_KIND_HTTP) > 0;
    }

    @Override
    public boolean tryCreateHandler(String key, LocalDateTime now) {
        return repository.tryInsert(key, null, now, KEY_KIND_HANDLER) > 0;
    }

    @Override
    public boolean tryResetFailed(String key, LocalDateTime now) {
        return tryResetFailed(key, null, now);
    }

    @Override
    public boolean tryResetFailed(String key, String requestHash, LocalDateTime now) {
        return repository.resetFailed(key, requestHash, now) == 1;
    }

    @Override
    public void markCompleted(String key, String responseBody, int responseStatus) {
        ReplayBody replayBody = replayBody(key, responseBody);
        int affected = repository.completeIfPending(key, replayBody.body(), responseStatus, replayBody.replayable());
        if (affected == 0) {
            // Not PENDING anymore: missing reservation is a bug, already-completed
            // is an idempotent replay (first-wins preserved) — return silently.
            var current = repository.findById(key);
            if (current.isEmpty()) {
                throw new IllegalStateException("Idempotency reservation is missing");
            }
        }
    }

    @Override
    public void markFailed(String key) {
        repository.failIfPending(key);
    }

    @Override
    public int failStalePending(LocalDateTime threshold) {
        return repository.failStalePending(threshold);
    }

    /**
     * P-3/R-I05: retention batch size. One batch is one short REQUIRES_NEW
     * transaction (see {@code deleteBatch}); the loop stops at the first short
     * batch, so steady-state cleanup is exactly one round trip and only a real
     * backlog pages.
     */
    static final int RETENTION_BATCH_SIZE = 1000;

    /**
     * R-I05: symmetric with the outbox retention budget. A backlog larger than
     * this drains over consecutive schedules instead of running an unbounded
     * loop inside the scheduler-lock transaction.
     */
    static final int MAX_BATCHES_PER_RUN = 10;

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = deleteBatch(
                    () -> repository.deleteExpiredTerminalRequestsBatch(threshold, RETENTION_BATCH_SIZE));
            total += deleted;
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    @Override
    public int deleteExpiredHandlerKeys(LocalDateTime threshold) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            int deleted = deleteBatch(
                    () -> repository.deleteExpiredHandlerKeysBatch(threshold, RETENTION_BATCH_SIZE));
            total += deleted;
            if (deleted < RETENTION_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private int deleteBatch(IntSupplier batchDelete) {
        if (retentionTransaction == null) {
            return batchDelete.getAsInt();
        }
        Integer deleted = retentionTransaction.execute(status -> batchDelete.getAsInt());
        return deleted != null ? deleted : 0;
    }

    private static TransactionTemplate retentionTransaction(PlatformTransactionManager transactionManager,
            TransactionProperties transactionProperties) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setTimeout(transactionProperties.timeoutSeconds());
        return template;
    }

    private static ReplayBody replayBody(String key, String responseBody) {
        if (responseBody != null && responseBody.length() > MAX_RESPONSE_BODY_CHARS) {
            log.warn("Idempotency replay body not stored (too large to replay without corruption): key={}, chars={}",
                    key, responseBody.length());
            return new ReplayBody(null, false);
        }
        return new ReplayBody(responseBody, true);
    }

    private record ReplayBody(String body, boolean replayable) {}
}
