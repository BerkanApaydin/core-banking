package com.bank.app.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {
    int deleteByCreatedAtBefore(LocalDateTime threshold);

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key_value, status, created_at) VALUES (:key, 'PENDING', :now) ON CONFLICT (key_value) DO NOTHING", nativeQuery = true)
    int tryInsert(@Param("key") String key, @Param("now") LocalDateTime now);
}
