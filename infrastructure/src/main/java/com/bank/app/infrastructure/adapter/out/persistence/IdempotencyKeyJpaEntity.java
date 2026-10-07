package com.bank.app.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyJpaEntity implements Persistable<String> {

    @Id
    @Column(name = "key_value")
    private String key;

    /**
     * Same assigned-id merge trap as {@code OutboxJpaEntity}: fresh keys are
     * always new rows, so persist() directly instead of SELECT-before-INSERT.
     */
    @Transient
    private boolean isNew = true;

    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Explicit key-space discriminator (V38): {@code HTTP} for request keys,
     * {@code HANDLER} for outbox handler dedup keys. Replaces string-prefix
     * matching, which could not use an index and misclassified near-misses.
     */
    @Column(name = "key_kind", nullable = false)
    private String keyKind = "HTTP";

    @Column(name = "response_body", length = 10000)
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    public IdempotencyKeyJpaEntity() {}

    public IdempotencyKeyJpaEntity(String key, String status, String responseBody, LocalDateTime createdAt) {
        this(key, status, responseBody, null, createdAt);
    }

    public IdempotencyKeyJpaEntity(String key, String status, String responseBody, Integer responseStatus, LocalDateTime createdAt) {
        this.key = key;
        this.status = status;
        this.responseBody = responseBody;
        this.responseStatus = responseStatus;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PrePersist
    void markNotNew() {
        this.isNew = false;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getRequestHash() { return requestHash; }

    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getKeyKind() { return keyKind; }

    public void setKeyKind(String keyKind) { this.keyKind = keyKind; }
}
