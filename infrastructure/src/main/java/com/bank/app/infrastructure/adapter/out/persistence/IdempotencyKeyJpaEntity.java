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

    /**
     * Replay body for a COMPLETED key. Unbounded TEXT since V53 (was
     * VARCHAR(10000)): completion joins the business transaction, so a
     * length cap here used to roll the business mutation back on oversized
     * responses. R-I06: bodies above the replay cap are not truncated (that
     * produced corrupt JSON); they are stored as NULL with
     * {@code responseReplayable = false}.
     */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    /**
     * R-I06: a body larger than the replay cap is stored as NULL with this
     * flag false. Replaying a character-truncated JSON fragment would return
     * corrupt JSON to the retried caller; the replay path returns an explicit
     * problem response instead.
     */
    @Column(name = "response_replayable", nullable = false)
    private boolean responseReplayable = true;

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

    public boolean isResponseReplayable() {
        return responseReplayable;
    }

    public void setResponseReplayable(boolean responseReplayable) {
        this.responseReplayable = responseReplayable;
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
