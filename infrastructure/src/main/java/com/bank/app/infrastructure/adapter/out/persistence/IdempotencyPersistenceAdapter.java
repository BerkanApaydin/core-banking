package com.bank.app.infrastructure.adapter.out.persistence;

import com.bank.app.common.application.port.out.IdempotencyPort;

import org.springframework.dao.DataIntegrityViolationException;
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
                        e.getCreatedAt()));
    }

    @Override
    public boolean tryCreate(String key, LocalDateTime now) {
        try {
            IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity(key, "PENDING", null, null, now);
            repository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    @Override
    public void markCompleted(String key, String responseBody, int responseStatus) {
        repository.findById(key).ifPresent(entity -> {
            entity.setStatus("COMPLETED");
            entity.setResponseBody(responseBody);
            entity.setResponseStatus(responseStatus);
            repository.save(entity);
        });
    }

    @Override
    public void markFailed(String key) {
        repository.findById(key).ifPresent(entity -> {
            entity.setStatus("FAILED");
            repository.save(entity);
        });
    }

    @Override
    public void deleteById(String key) {
        repository.deleteById(key);
    }

    @Override
    public int deleteExpired(LocalDateTime threshold) {
        return repository.deleteByCreatedAtBefore(threshold);
    }
}
