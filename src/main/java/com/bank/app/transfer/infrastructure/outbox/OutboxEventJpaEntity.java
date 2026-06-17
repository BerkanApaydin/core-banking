package com.bank.app.transfer.infrastructure.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, length = 4000)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "dead_letter", nullable = false)
    private boolean deadLetter;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public OutboxEventJpaEntity() {}

    public OutboxEventJpaEntity(String id, String aggregateType, String aggregateId, String eventType,
                                String payload, LocalDateTime createdAt, boolean processed, LocalDateTime processedAt) {
        this(id, aggregateType, aggregateId, eventType, payload, createdAt, processed, processedAt, 0, false, null);
    }

    public OutboxEventJpaEntity(String id, String aggregateType, String aggregateId, String eventType,
                                String payload, LocalDateTime createdAt, boolean processed, LocalDateTime processedAt,
                                int retryCount, boolean deadLetter, String lastError) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.processed = processed;
        this.processedAt = processedAt;
        this.retryCount = retryCount;
        this.deadLetter = deadLetter;
        this.lastError = lastError;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isDeadLetter() {
        return deadLetter;
    }

    public void setDeadLetter(boolean deadLetter) {
        this.deadLetter = deadLetter;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
