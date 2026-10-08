package com.bank.app.audit.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    List<AuditLogJpaEntity> findAllByOrderByTimestampDescIdDesc(Pageable pageable);

    List<AuditLogJpaEntity> findByActorUserIdOrderByTimestampDescIdDesc(Long actorUserId, Pageable pageable);

    List<AuditLogJpaEntity> findByTimestampBetweenOrderByTimestampDescIdDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLogJpaEntity a WHERE a.timestamp < :cutoff")
    int deleteByTimestampBefore(@Param("cutoff") LocalDateTime cutoff);
}
