package com.bank.app.audit.application.port.in;

import com.bank.app.audit.application.dto.AuditLogResponse;
import java.util.List;

public interface GetAuditLogsQuery {

    List<AuditLogResponse> execute(int limit);

    /**
     * Per-identity listing for incident review. Default delegates to the full
     * listing so existing fakes/mocks that only stub {@code execute(limit)}
     * keep working; the production implementation overrides it.
     */
    default List<AuditLogResponse> execute(Long actorUserId, int limit) {
        return execute(limit);
    }
}
