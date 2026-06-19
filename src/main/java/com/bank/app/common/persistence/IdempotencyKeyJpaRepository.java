package com.bank.app.common.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, String> {
    int deleteByCreatedAtBefore(LocalDateTime threshold);
}
