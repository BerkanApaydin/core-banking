package com.bank.app.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {
    int deleteByCreatedAtBefore(LocalDateTime threshold);
}
