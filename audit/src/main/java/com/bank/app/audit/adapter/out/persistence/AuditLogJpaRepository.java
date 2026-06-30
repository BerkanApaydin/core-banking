package com.bank.app.audit.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
