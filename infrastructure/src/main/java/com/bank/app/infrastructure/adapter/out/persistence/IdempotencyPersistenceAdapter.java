package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.IdempotencyPort;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class IdempotencyPersistenceAdapter implements IdempotencyPort {

    private final IdempotencyKeyJpaRepository repository;

    public IdempotencyPersistenceAdapter(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Entry> findById(@NonNull String key) {
        return repository.findById(key)
                .map(e -> new Entry(e.getKey(), e.getStatus(), e.getResponseBody(), e.getResponseStatus(),
                        e.getCreatedAt(), e.getRequestHash()));
    }

    @Override
    public boolean tryCreate(String key, LocalDateTime now) {
        return tryCreate(key, null, now);
    }

    @Override
    public boolean tryCreate(String key, String requestHash, LocalDateTime now) {
        return repository.tryInsert(key, requestHash, now) > 0;
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
        int affected = repository.completeIfPending(key, responseBody, responseStatus);
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
     * P-3: retention batch size. One batch is one short transaction; the loop
     * stops at the first short batch, so steady-state cleanup is exactly one
     * round trip and only a real backlog pages.
     */
    static final int RETENTION_BATCH_SIZE = 1000;

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpiredTerminalRequestsBatch(threshold, RETENTION_BATCH_SIZE);
            total += deleted;
        } while (deleted >= RETENTION_BATCH_SIZE);
        return total;
    }

    @Override
    public int deleteExpiredHandlerKeys(LocalDateTime threshold) {
        int total = 0;
        int deleted;
        do {
            deleted = repository.deleteExpiredHandlerKeysBatch(threshold, RETENTION_BATCH_SIZE);
            total += deleted;
        } while (deleted >= RETENTION_BATCH_SIZE);
        return total;
    }
}
