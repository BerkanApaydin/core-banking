package com.bank.app.common.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxPort {
    void save(EventEntry entry);
    List<EventEntry> findAndLockUnprocessed(int limit, int partition);
    Optional<EventEntry> findByIdForUpdateSkipLocked(String id);
    void markProcessed(String id);
    void markFailed(String id, String error, int retryCount);
    void markDeadLetter(String id, String error, int retryCount);

    record EventEntry(String id, String aggregateType, String aggregateId, String eventType,
                      String payload, int retryCount, boolean processed, boolean deadLetter,
                      String lastError, int partition, LocalDateTime createdAt) {}
}
