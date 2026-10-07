package com.bank.app.audit.adapter.out.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    List<AuditLogJpaEntity> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    List<AuditLogJpaEntity> findByActorUserIdOrderByTimestampDescIdDesc(Long actorUserId, Pageable pageable);
}
