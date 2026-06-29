package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdempotencyGuard {

    private final IdempotencyPort idempotencyPort;

    public IdempotencyGuard(IdempotencyPort idempotencyPort) {
        this.idempotencyPort = idempotencyPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult startRequest(String key) {
        Objects.requireNonNull(key, "Idempotency key must not be null");
        Optional<Entry> existing = idempotencyPort.findById(key);
        if (existing.isPresent()) {
            Entry entry = existing.get();
            if ("PENDING".equals(entry.status())) {
                return IdempotencyResult.pending();
            } else if ("FAILED".equals(entry.status())) {
                idempotencyPort.deleteById(key);
                return IdempotencyResult.newRequest();
            } else {
                return IdempotencyResult.completed(entry.responseBody(), entry.responseStatus());
            }
        }
        if (idempotencyPort.tryCreate(key, LocalDateTime.now())) {
            return IdempotencyResult.newRequest();
        }
        Optional<Entry> race = idempotencyPort.findById(key);
        if (race.isPresent()) {
            Entry entry = race.get();
            if ("PENDING".equals(entry.status())) {
                return IdempotencyResult.pending();
            } else {
                return IdempotencyResult.completed(entry.responseBody(), entry.responseStatus());
            }
        }
        return IdempotencyResult.pending();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRequest(String key, String responseBody, int responseStatus) {
        Objects.requireNonNull(key, "Idempotency key must not be null");
        idempotencyPort.markCompleted(key, responseBody, responseStatus);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRequest(String key) {
        Objects.requireNonNull(key, "Idempotency key must not be null");
        idempotencyPort.markFailed(key);
    }

    public record IdempotencyResult(Status status, String responseBody, Integer responseStatus) {
        public IdempotencyResult {
            java.util.Objects.requireNonNull(status);
        }

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
