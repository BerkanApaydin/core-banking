package com.bank.app.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "key_value")
    private String key;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "response_body", length = 10000)
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

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
}
