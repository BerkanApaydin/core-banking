package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.dto.AuditLogResponse;
import com.bank.app.audit.application.port.in.GetAuditLogsQuery;
import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.common.application.port.in.ReadOnlyUseCase;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import java.util.List;

/**
 * Admin-only audit trail read. Role enforcement lives here (application
 * layer, next to the object-level checks of the other bounded contexts),
 * not in Spring Security annotations — so the rule is unit-testable without
 * a web slice and visible to ArchUnit.
 *
 * <p>The {@code ROLE_ADMIN} literal is intentional: the audit module must not
 * compile against the user module's {@code Role} enum (module-boundary rule),
 * and the value is pinned by {@code chk_users_role} plus the JWT role claim.
 * Provisioning stays out-of-band (ops creates the first admin directly;
 * {@code User.assignRole} remains banned until token versioning exists).
 */
@ReadOnlyUseCase
public class GetAuditLogsQueryImpl implements GetAuditLogsQuery {

    static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final LoadAuditLogPort loadAuditLogPort;
    private final UserContextService userContextService;
    private final int maxLimit;

    public GetAuditLogsQueryImpl(LoadAuditLogPort loadAuditLogPort,
                                 UserContextService userContextService,
                                 int maxLimit) {
        this.loadAuditLogPort = loadAuditLogPort;
        this.userContextService = userContextService;
        this.maxLimit = maxLimit > 0 ? maxLimit : 500;
    }

    @Override
    public List<AuditLogResponse> execute(int limit) {
        requireAdmin();
        int capped = cap(limit);
        return loadAuditLogPort.findRecent(capped).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    @Override
    public List<AuditLogResponse> execute(Long actorUserId, int limit) {
        requireAdmin();
        int capped = cap(limit);
        if (actorUserId == null) {
            return loadAuditLogPort.findRecent(capped).stream()
                    .map(AuditLogResponse::from)
                    .toList();
        }
        return loadAuditLogPort.findByActor(actorUserId, capped).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    private void requireAdmin() {
        userContextService.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("You must be logged in to perform this action."));
        if (!userContextService.hasRole(ADMIN_ROLE)) {
            throw new AuthorizationException("Admin role required.");
        }
    }

    private int cap(int limit) {
        return Math.max(Math.min(limit, maxLimit), 1);
    }
}
