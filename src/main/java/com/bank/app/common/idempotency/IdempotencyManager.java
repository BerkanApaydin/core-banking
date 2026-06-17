package com.bank.app.common.idempotency;

import com.bank.app.common.persistence.IdempotencyKeyJpaEntity;
import com.bank.app.common.persistence.SpringDataIdempotencyKeyRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.lang.NonNull;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdempotencyManager {

    private final SpringDataIdempotencyKeyRepo repo;

    public IdempotencyManager(SpringDataIdempotencyKeyRepo repo) {
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
                return IdempotencyResult.completed(entity.getResponseBody());
            }
        }
        try {
            IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity(key, "PENDING", null, LocalDateTime.now());
            repo.saveAndFlush(entity);
            return IdempotencyResult.newRequest();
        } catch (Exception ex) {
            // Constraint violation fallback
            IdempotencyKeyJpaEntity entity = repo.findById(key)
                    .orElseThrow(() -> new IllegalStateException("Conflict on idempotency key insertion", ex));
            if ("PENDING".equals(entity.getStatus())) {
                return IdempotencyResult.pending();
            } else {
                return IdempotencyResult.completed(entity.getResponseBody());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRequest(@NonNull String key, String responseBody) {
        Objects.requireNonNull(key, "Idempotency key null olamaz");
        repo.findById(key).ifPresent(entity -> {
            entity.setStatus("COMPLETED");
            entity.setResponseBody(responseBody);
            repo.save(entity);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRequest(@NonNull String key) {
        Objects.requireNonNull(key, "Idempotency key null olamaz");
        repo.deleteById(key);
    }

    public record IdempotencyResult(Status status, String responseBody) {
        public enum Status { NEW, PENDING, COMPLETED }

        public static IdempotencyResult pending() {
            return new IdempotencyResult(Status.PENDING, null);
        }

        public static IdempotencyResult completed(String responseBody) {
            return new IdempotencyResult(Status.COMPLETED, responseBody);
        }

        public static IdempotencyResult newRequest() {
            return new IdempotencyResult(Status.NEW, null);
        }

        public boolean isCompleted() {
            return status == Status.COMPLETED;
        }

        public boolean isPending() {
            return status == Status.PENDING;
        }
    }
}
