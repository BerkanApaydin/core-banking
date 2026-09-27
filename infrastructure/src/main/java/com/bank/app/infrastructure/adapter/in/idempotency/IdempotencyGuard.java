package com.bank.app.infrastructure.adapter.in.idempotency;

import com.bank.app.common.application.port.out.IdempotencyPort;
import com.bank.app.common.application.port.out.IdempotencyPort.Entry;
import com.bank.app.common.domain.exception.ConcurrentRequestException;
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
        return startRequest(key, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyResult startRequest(String key, String requestHash) {
        Objects.requireNonNull(key, "Idempotency key must not be null");
        Optional<Entry> existing = idempotencyPort.findById(key);
        if (existing.isPresent()) {
            return claimOrReadExisting(key, requestHash, existing.get());
        }
        boolean created = requestHash == null
                ? idempotencyPort.tryCreate(key, LocalDateTime.now())
                : idempotencyPort.tryCreate(key, requestHash, LocalDateTime.now());
        if (created) {
            return IdempotencyResult.newRequest();
        }
        Optional<Entry> race = idempotencyPort.findById(key);
        if (race.isPresent()) {
            return claimOrReadExisting(key, requestHash, race.get());
        }
        return IdempotencyResult.pending();
    }

    private IdempotencyResult claimOrReadExisting(String key, String requestHash, Entry entry) {
        if (requestHash != null && !requestHash.equals(entry.requestHash())) {
            throw new ConcurrentRequestException("error.idempotency_payload_conflict", null,
                    "Idempotency-Key was already used with a different request.");
        }
        return switch (entry.status()) {
            case "PENDING" -> IdempotencyResult.pending();
            case "COMPLETED" -> IdempotencyResult.completed(entry.responseBody(), entry.responseStatus());
            case "FAILED" -> {
                boolean claimed = requestHash == null
                        ? idempotencyPort.tryResetFailed(key, LocalDateTime.now())
                        : idempotencyPort.tryResetFailed(key, requestHash, LocalDateTime.now());
                if (claimed) {
                    yield IdempotencyResult.newRequest();
                }
                // Another request won the FAILED -> PENDING transition. Never
                // proceed without owning a live reservation.
                Optional<Entry> winner = idempotencyPort.findById(key);
                if (winner.isPresent() && "COMPLETED".equals(winner.get().status())) {
                    yield IdempotencyResult.completed(winner.get().responseBody(), winner.get().responseStatus());
                }
                yield IdempotencyResult.pending();
            }
            default -> throw new IllegalStateException("Unknown idempotency status: " + entry.status());
        };
    }

    // The successful outcome must commit with the business mutation. The
    // controller aspect opens that transaction before invoking the use case.
    @Transactional(propagation = Propagation.REQUIRED)
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
            Objects.requireNonNull(status);
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
