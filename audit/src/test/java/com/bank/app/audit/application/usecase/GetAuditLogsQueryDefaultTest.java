package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.dto.AuditLogResponse;
import com.bank.app.audit.application.port.in.GetAuditLogsQuery;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GetAuditLogsQueryDefaultTest {

    @Test
    void defaultActorOverloadShouldDelegateToLimitOverload() {
        AuditLogResponse response = AuditLogResponse.from(
                new AuditLog(1L, "alice", AuditAction.TRANSFER_EXECUTED,
                        "Transfer completed. Transfer ID: 7", LocalDateTime.now()));
        GetAuditLogsQuery query = new GetAuditLogsQuery() {
            @Override
            public List<AuditLogResponse> execute(int limit) {
                assertThat(limit).isEqualTo(50);
                return List.of(response);
            }
        };

        List<AuditLogResponse> result = query.execute(7L, 50);

        assertThat(result).containsExactly(response);
    }
}
