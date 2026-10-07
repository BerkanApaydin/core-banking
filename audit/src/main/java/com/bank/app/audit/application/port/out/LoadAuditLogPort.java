package com.bank.app.audit.application.port.out;

import com.bank.app.audit.domain.AuditLog;
import java.util.List;

public interface LoadAuditLogPort {

    List<AuditLog> findRecent(int limit);

    List<AuditLog> findByActor(Long actorUserId, int limit);
}
