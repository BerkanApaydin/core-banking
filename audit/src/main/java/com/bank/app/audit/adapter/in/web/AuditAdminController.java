package com.bank.app.audit.adapter.in.web;

import com.bank.app.audit.application.dto.AuditLogResponse;
import com.bank.app.audit.application.port.in.GetAuditLogsQuery;
import com.bank.app.common.adapter.in.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only audit trail read (ROLE_ADMIN, enforced in the use case).
 * The first admin account is provisioned out-of-band by operations;
 * self-registration always yields ROLE_USER.
 */
@RestController
@ApiVersion("v1")
@Validated
@RequestMapping("/admin/audit-logs")
public class AuditAdminController {

    private final GetAuditLogsQuery getAuditLogsQuery;

    public AuditAdminController(GetAuditLogsQuery getAuditLogsQuery) {
        this.getAuditLogsQuery = getAuditLogsQuery;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit,
            @RequestParam(required = false) Long actorUserId) {
        if (actorUserId == null) {
            return ResponseEntity.ok(getAuditLogsQuery.execute(limit));
        }
        return ResponseEntity.ok(getAuditLogsQuery.execute(actorUserId, limit));
    }
}
