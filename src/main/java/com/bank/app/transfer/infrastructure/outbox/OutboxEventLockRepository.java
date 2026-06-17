package com.bank.app.transfer.infrastructure.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxEventLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.outbox.use-skip-locked:true}")
    private boolean useSkipLocked;

    @SuppressWarnings("unchecked")
    public List<OutboxEventJpaEntity> findAndLockUnprocessed(int limit) {
        if (useSkipLocked) {
            return entityManager.createNativeQuery("""
                    SELECT * FROM outbox_events
                    WHERE processed = false AND dead_letter = false
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """, OutboxEventJpaEntity.class)
                    .setParameter("limit", limit)
                    .getResultList();
        }
        return entityManager.createQuery("""
                SELECT e FROM OutboxEventJpaEntity e
                WHERE e.processed = false AND e.deadLetter = false
                ORDER BY e.createdAt ASC
                """, OutboxEventJpaEntity.class)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }
}
