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
