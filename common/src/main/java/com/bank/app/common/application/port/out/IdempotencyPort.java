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
    void deleteById(String key);
    int deleteExpired(LocalDateTime threshold);

    record Entry(String key, String status, String responseBody, Integer responseStatus,
                 LocalDateTime createdAt, String requestHash) {
        public Entry(String key, String status, String responseBody, Integer responseStatus,
                     LocalDateTime createdAt) {
            this(key, status, responseBody, responseStatus, createdAt, null);
        }
    }
    record SaveResult(boolean created) {}
}
