package com.bank.app.common.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyPort {
    Optional<Entry> findById(String key);
    boolean tryCreate(String key, LocalDateTime now);
    boolean tryCreate(String key, String requestHash, LocalDateTime now);
    boolean tryResetFailed(String key, LocalDateTime now);
    boolean tryResetFailed(String key, String requestHash, LocalDateTime now);
    void markCompleted(String key, String responseBody, int responseStatus);
    void markFailed(String key);

    /**
     * Crash-window recovery for HTTP idempotency reservations: a JVM crash
     * between the REQUIRES_NEW claim commit and the business outcome leaves a
     * PENDING row whose owner will never complete or fail it. Without this,
     * the same key retries {@code PENDING} forever (409 CONTENDED) even
     * though no work is in flight. Transitions stale HTTP PENDING rows
     * (older than the threshold) to FAILED so the next retry can
     * {@code tryResetFailed} and re-execute. Backed by
     * {@code idx_idempotency_pending_http_kind} (V38).
     *
     * @return number of rows transitioned to FAILED
     */
    int failStalePending(LocalDateTime threshold);
    int deleteExpired(LocalDateTime threshold);

    /**
     * Retention hygiene for outbox handler dedup keys ({@code outbox_handler_*}):
     * only non-pending rows older than the threshold. PENDING entries are
     * preserved like the HTTP cleanup does — a stuck reservation must stay
     * visible instead of silently reopening. Run only after (or with the same
     * cutoff as) outbox row retention, never before.
     */
    int deleteExpiredHandlerKeys(LocalDateTime threshold);

    record Entry(String key, String status, String responseBody, Integer responseStatus,
                 LocalDateTime createdAt, String requestHash) {
        public Entry(String key, String status, String responseBody, Integer responseStatus,
                     LocalDateTime createdAt) {
            this(key, status, responseBody, responseStatus, createdAt, null);
        }
    }
}
