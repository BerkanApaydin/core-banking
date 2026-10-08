package com.bank.app.audit.application.port.out;

import com.bank.app.audit.domain.AuditLog;
import java.time.LocalDateTime;
import java.util.List;

public interface LoadAuditLogPort {

    List<AuditLog> findRecent(int limit);

    List<AuditLog> findByActor(Long actorUserId, int limit);

    List<AuditLog> findByTimeRange(LocalDateTime from, LocalDateTime to, int limit);
}
