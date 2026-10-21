package com.bank.app.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {
    /**
     * P-3: batched retention deletes (same IN-subquery pattern as outbox
     * retention): one unbounded DELETE would hold the whole expired tail in a
     * single long transaction. Callers loop until a batch comes back short.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM idempotency_keys WHERE key_value IN ("
            + "SELECT key_value FROM idempotency_keys WHERE created_at < :threshold "
            + "AND status <> 'PENDING' "
            + "AND key_kind = 'HTTP' LIMIT :batch)", nativeQuery = true)
    int deleteExpiredTerminalRequestsBatch(@Param("threshold") LocalDateTime threshold,
                                           @Param("batch") int batch);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM idempotency_keys WHERE key_value IN ("
            + "SELECT key_value FROM idempotency_keys WHERE created_at < :threshold "
            + "AND status <> 'PENDING' "
            + "AND key_kind = 'HANDLER' LIMIT :batch)", nativeQuery = true)
    int deleteExpiredHandlerKeysBatch(@Param("threshold") LocalDateTime threshold,
                                      @Param("batch") int batch);

    /**
     * Inserts a PENDING reservation.
     *
     * <p>{@code key_kind} is an EXPLICIT parameter: V38 replaced the
     * string-prefix matching ("does the key start with {@code outbox_handler_}?")
     * with an explicit discriminator, because key_kind drives the retention
     * split — a misclassified key is deleted by the wrong sweep. Deriving it
     * from the key text again re-introduced exactly the bug V38 removed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO idempotency_keys (key_value, status, created_at, request_hash, key_kind) "
            + "VALUES (:key, 'PENDING', :now, :requestHash, :keyKind) "
            + "ON CONFLICT (key_value) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("requestHash") String requestHash,
                  @Param("now") LocalDateTime now, @Param("keyKind") String keyKind);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status = 'PENDING', response_body = NULL, response_status = NULL, "
            + "response_replayable = TRUE, request_hash = :requestHash, created_at = :now "
            + "WHERE key_value = :key AND status = 'FAILED'", nativeQuery = true)
    int resetFailed(@Param("key") String key, @Param("requestHash") String requestHash, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='COMPLETED', response_body=:body, "
            + "response_status=:status, response_replayable=:replayable "
            + "WHERE key_value=:key AND status='PENDING'", nativeQuery=true)
    int completeIfPending(@Param("key") String key, @Param("body") String body,
                          @Param("status") int status, @Param("replayable") boolean replayable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='FAILED' WHERE key_value=:key AND status='PENDING'", nativeQuery=true)
    int failIfPending(@Param("key") String key);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE idempotency_keys SET status='FAILED' "
            + "WHERE status='PENDING' AND key_kind='HTTP' AND created_at < :threshold", nativeQuery = true)
    int failStalePending(@Param("threshold") LocalDateTime threshold);
}
