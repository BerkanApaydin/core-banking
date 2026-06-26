package com.bank.app.common.application.port.out;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.lang.NonNull;

public interface IdempotencyPort {
    Optional<Entry> findById(@NonNull String key);
    boolean tryCreate(String key, LocalDateTime now);
    void markCompleted(@NonNull String key, String responseBody, int responseStatus);
    void markFailed(@NonNull String key);
    void deleteById(@NonNull String key);
    int deleteExpired(LocalDateTime threshold);

    record Entry(String key, String status, String responseBody, Integer responseStatus, LocalDateTime createdAt) {}
    record SaveResult(boolean created) {}
}
