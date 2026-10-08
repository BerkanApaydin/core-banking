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

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        return repository.deleteExpiredTerminalRequests(threshold);
    }

    @Override
    public int deleteExpiredHandlerKeys(LocalDateTime threshold) {
        return repository.deleteExpiredHandlerKeys(threshold);
    }
}
