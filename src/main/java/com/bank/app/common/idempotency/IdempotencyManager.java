package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.IdempotencyKeyJpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdempotencyManager {

    private final IdempotencyKeyJpaRepository repo;

    public IdempotencyManager(IdempotencyKeyJpaRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult startRequest(@NonNull String key) {
        Objects.requireNonNull(key, "Idempotency key null olamaz");
        Optional<IdempotencyKeyJpaEntity> existing = repo.findById(key);
        if (existing.isPresent()) {
            IdempotencyKeyJpaEntity entity = existing.get();
            if ("PENDING".equals(entity.getStatus())) {
                return IdempotencyResult.pending();
            } else {
                return IdempotencyResult.completed(entity.getResponseBody(), entity.getResponseStatus());
            }
        }
        try {
            IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity(key, "PENDING", null, null, LocalDateTime.now());
            repo.saveAndFlush(entity);
            return IdempotencyResult.newRequest();
        } catch (Exception ex) {
            IdempotencyKeyJpaEntity entity = repo.findById(key)
                    .orElseThrow(() -> new IllegalStateException("Conflict on idempotency key insertion", ex));
            if ("PENDING".equals(entity.getStatus())) {
                return IdempotencyResult.pending();
            } else {
                return IdempotencyResult.completed(entity.getResponseBody(), entity.getResponseStatus());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRequest(@NonNull String key, String responseBody, int responseStatus) {
        Objects.requireNonNull(key, "Idempotency key null olamaz");
        repo.findById(key).ifPresent(entity -> {
            entity.setStatus("COMPLETED");
            entity.setResponseBody(responseBody);
            entity.setResponseStatus(responseStatus);
            repo.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRequest(@NonNull String key) {
        Objects.requireNonNull(key, "Idempotency key null olamaz");
        repo.deleteById(key);
    }

    public record IdempotencyResult(Status status, String responseBody, Integer responseStatus) {
        public enum Status { NEW, PENDING, COMPLETED }

        public static IdempotencyResult pending() {
            return new IdempotencyResult(Status.PENDING, null, null);
        }

        public static IdempotencyResult completed(String responseBody, Integer responseStatus) {
            return new IdempotencyResult(Status.COMPLETED, responseBody, responseStatus);
        }

        public static IdempotencyResult newRequest() {
            return new IdempotencyResult(Status.NEW, null, null);
        }

        public boolean isCompleted() {
            return status == Status.COMPLETED;
        }

        public boolean isPending() {
            return status == Status.PENDING;
        }
    }
}
