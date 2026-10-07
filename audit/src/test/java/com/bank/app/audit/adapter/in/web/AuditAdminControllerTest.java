package com.bank.app.audit.adapter.in.web;

import com.bank.app.audit.application.dto.AuditLogResponse;
import com.bank.app.audit.application.port.in.GetAuditLogsQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Plain unit test (no Spring slice): parameter validation (@Min/@Max) and the
// /api/v1 version prefix are covered by AuditAdminAuthorizationIT instead.
@ExtendWith(MockitoExtension.class)
class AuditAdminControllerTest {

    @Mock
    private GetAuditLogsQuery getAuditLogsQuery;

    @Test
    void shouldDelegateToQueryAndReturnOk() {
        var controller = new AuditAdminController(getAuditLogsQuery);
        var rows = List.of(new AuditLogResponse(1L, "alice", 7L, "TRANSFER_EXECUTED",
                "Transfer completed. Transfer ID: 7", LocalDateTime.of(2026, 9, 1, 12, 0)));
        when(getAuditLogsQuery.execute(10)).thenReturn(rows);

        var response = controller.list(10, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rows);
        verify(getAuditLogsQuery).execute(10);
    }

    @Test
    void shouldDelegateActorFilterToQuery() {
        var controller = new AuditAdminController(getAuditLogsQuery);
        var rows = List.of(new AuditLogResponse(1L, "alice", 7L, "TRANSFER_EXECUTED",
                "Transfer completed. Transfer ID: 7", LocalDateTime.of(2026, 9, 1, 12, 0)));
        when(getAuditLogsQuery.execute(7L, 10)).thenReturn(rows);

        var response = controller.list(10, 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rows);
        verify(getAuditLogsQuery).execute(7L, 10);
    }
}
