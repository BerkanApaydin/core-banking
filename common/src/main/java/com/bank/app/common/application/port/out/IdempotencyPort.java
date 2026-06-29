package com.bank.app.common.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyPort {
    Optional<Entry> findById(String key);
    boolean tryCreate(String key, LocalDateTime now);
    void markCompleted(String key, String responseBody, int responseStatus);
    void markFailed(String key);
    void deleteById(String key);
    int deleteExpired(LocalDateTime threshold);

    record Entry(String key, String status, String responseBody, Integer responseStatus, LocalDateTime createdAt) {}
    record SaveResult(boolean created) {}
}
