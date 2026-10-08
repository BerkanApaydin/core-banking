package com.bank.app.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {
    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE created_at < :threshold "
            + "AND status <> 'PENDING' "
            + "AND key_kind = 'HTTP'", nativeQuery = true)
    int deleteExpiredTerminalRequests(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE created_at < :threshold "
            + "AND status <> 'PENDING' "
            + "AND key_kind = 'HANDLER'", nativeQuery = true)
    int deleteExpiredHandlerKeys(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key_value, status, created_at, request_hash, key_kind) "
            + "VALUES (:key, 'PENDING', :now, :requestHash, "
            + "CASE WHEN left(:key, length('outbox_handler_')) = 'outbox_handler_' THEN 'HANDLER' ELSE 'HTTP' END) "
            + "ON CONFLICT (key_value) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status = 'PENDING', response_body = NULL, response_status = NULL, request_hash = :requestHash, created_at = :now "
            + "WHERE key_value = :key AND status = 'FAILED'", nativeQuery = true)
    int resetFailed(@Param("key") String key, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='COMPLETED', response_body=:body, response_status=:status WHERE key_value=:key AND status='PENDING'", nativeQuery=true)
    int completeIfPending(@Param("key") String key, @Param("body") String body, @Param("status") int status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='FAILED' WHERE key_value=:key AND status='PENDING'", nativeQuery=true)
    int failIfPending(@Param("key") String key);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='FAILED' "
            + "WHERE status='PENDING' AND key_kind='HTTP' AND created_at < :threshold", nativeQuery = true)
    int failStalePending(@Param("threshold") LocalDateTime threshold);
}
