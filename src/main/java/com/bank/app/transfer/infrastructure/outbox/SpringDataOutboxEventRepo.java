package com.bank.app.transfer.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SpringDataOutboxEventRepo extends JpaRepository<OutboxEventJpaEntity, String> {
    List<OutboxEventJpaEntity> findTop10ByProcessedFalseOrderByCreatedAtAsc();
}
