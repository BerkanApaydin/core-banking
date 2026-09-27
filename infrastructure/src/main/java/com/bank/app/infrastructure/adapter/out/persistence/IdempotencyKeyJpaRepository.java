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
            + "AND left(key_value, length('outbox_handler_')) <> 'outbox_handler_'", nativeQuery = true)
    int deleteExpiredTerminalRequests(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key_value, status, created_at, request_hash) VALUES (:key, 'PENDING', :now, :requestHash) ON CONFLICT (key_value) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status = 'PENDING', response_body = NULL, response_status = NULL, request_hash = :requestHash, created_at = :now "
            + "WHERE key_value = :key AND status = 'FAILED'", nativeQuery = true)
    int resetFailed(@Param("key") String key, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);
}
