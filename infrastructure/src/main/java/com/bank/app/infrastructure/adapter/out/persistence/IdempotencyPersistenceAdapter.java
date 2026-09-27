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
        var entity = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency reservation is missing"));
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("Idempotency reservation is not pending");
        }
        entity.setStatus("COMPLETED");
        entity.setResponseBody(responseBody);
        entity.setResponseStatus(responseStatus);
        repository.save(entity);
    }

    @Override
    public void markFailed(String key) {
        repository.findById(key).ifPresent(entity -> {
            if ("PENDING".equals(entity.getStatus())) {
                entity.setStatus("FAILED");
                repository.save(entity);
            }
        });
    }

    @Override
    public void deleteById(String key) {
        repository.deleteById(key);
    }

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        return repository.deleteExpiredTerminalRequests(threshold);
    }
}
