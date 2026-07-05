package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.infrastructure.adapter.out.persistence.OutboxJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OutboxLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.outbox.use-skip-locked:true}")
    private boolean useSkipLocked;

    @SuppressWarnings("unchecked")
    public List<OutboxJpaEntity> findAndLockUnprocessed(int limit, int partition) {
        if (useSkipLocked) {
            var query = entityManager.createNativeQuery("""
                    SELECT * FROM outbox_events
                    WHERE processed = false AND dead_letter = false
                    """ + (partition >= 0 ? " AND partition = :partition " : "") + """
                    ORDER BY created_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """, OutboxJpaEntity.class)
                    .setParameter("limit", limit);
            if (partition >= 0) {
                query.setParameter("partition", partition);
            }
            return query.getResultList();
        }
        var jpql = "SELECT e FROM OutboxJpaEntity e WHERE e.processed = false AND e.deadLetter = false"
                + (partition >= 0 ? " AND e.partition = :partition" : "")
                + " ORDER BY e.createdAt ASC";
        var query = entityManager.createQuery(jpql, OutboxJpaEntity.class)
                .setMaxResults(limit)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE);
        if (partition >= 0) {
            query.setParameter("partition", partition);
        }
        return query.getResultList();
    }
}
