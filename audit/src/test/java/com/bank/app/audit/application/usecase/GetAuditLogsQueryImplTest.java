package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.dto.AuditLogResponse;
import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAuditLogsQueryImplTest {

    @Mock
    private LoadAuditLogPort loadAuditLogPort;

    @Mock
    private UserContextService userContextService;

    private GetAuditLogsQueryImpl query;

    @BeforeEach
    void setUp() {
        query = new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService, 500);
    }

    @Test
    void shouldReturnLogsForAdmin() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        var log = new AuditLog(1L, "alice", AuditAction.TRANSFER_EXECUTED,
                "Transfer completed. Transfer ID: 7", LocalDateTime.now());
        when(loadAuditLogPort.findRecent(50)).thenReturn(List.of(log));

        List<AuditLogResponse> response = query.execute(50);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).action()).isEqualTo("TRANSFER_EXECUTED");
        assertThat(response.get(0).username()).isEqualTo("alice");
    }

    @Test
    void shouldRejectNonAdmin() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> query.execute(50))
                .isExactlyInstanceOf(AuthorizationException.class)
                .hasMessage("Admin role required.");

        verify(loadAuditLogPort, never()).findRecent(anyInt());
    }

    @Test
    void shouldRejectAnonymous() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(50))
                .isExactlyInstanceOf(AuthorizationException.class);

        verify(loadAuditLogPort, never()).findRecent(anyInt());
    }

    @Test
    void shouldCapLimitAtConfiguredMaximum() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(loadAuditLogPort.findRecent(500)).thenReturn(List.of());

        query.execute(10_000);

        verify(loadAuditLogPort).findRecent(500);
    }

    @Test
    void shouldFloorLimitAtOne() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(loadAuditLogPort.findRecent(1)).thenReturn(List.of());

        query.execute(0);

        verify(loadAuditLogPort).findRecent(1);
    }

    @Test
    void shouldReturnActorLogsForAdmin() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        var log = new AuditLog(1L, "alice", AuditAction.TRANSFER_EXECUTED,
                "Transfer completed. Transfer ID: 7", LocalDateTime.now(), 7L);
        when(loadAuditLogPort.findByActor(7L, 50)).thenReturn(List.of(log));

        List<AuditLogResponse> response = query.execute(7L, 50);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).actorUserId()).isEqualTo(7L);
        verify(loadAuditLogPort, never()).findRecent(anyInt());
    }

    @Test
    void shouldFallBackToRecentWhenActorIsNull() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        var log = new AuditLog(2L, "bob", AuditAction.LOGIN_SUCCEEDED,
                "Login succeeded.", LocalDateTime.now());
        when(loadAuditLogPort.findRecent(50)).thenReturn(List.of(log));

        List<AuditLogResponse> response = query.execute(null, 50);

        verify(loadAuditLogPort).findRecent(50);
        // Kills the EmptyObjectReturn mutant on the null-actor branch:
        // the mutant would return an empty list instead of the mapped port result.
        assertThat(response).hasSize(1);
        assertThat(response.get(0).username()).isEqualTo("bob");
    }

    @Test
    void shouldDefaultNonPositiveMaxLimitTo500() {
        // Kills both the Boundary (<= vs <) and Negate mutants on
        // `maxLimit > 0 ? maxLimit : 500`: 0 must fall back to 500.
        var zeroQuery = new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService, 0);
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(loadAuditLogPort.findRecent(500)).thenReturn(List.of());

        zeroQuery.execute(10_000);

        verify(loadAuditLogPort).findRecent(500);
    }

    @Test
    void shouldDefaultNegativeMaxLimitTo500() {
        var negativeQuery = new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService, -10);
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(loadAuditLogPort.findRecent(500)).thenReturn(List.of());

        negativeQuery.execute(10_000);

        verify(loadAuditLogPort).findRecent(500);
    }

    @Test
    void shouldHonorPositiveMaxLimit() {
        var customQuery = new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService, 25);
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(loadAuditLogPort.findRecent(25)).thenReturn(List.of());

        customQuery.execute(10_000);

        verify(loadAuditLogPort).findRecent(25);
    }

    @Test
    void shouldRejectActorQueryForNonAdmin() {
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(7L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(false);

        assertThatThrownBy(() -> query.execute(7L, 50))
                .isExactlyInstanceOf(AuthorizationException.class);

        verify(loadAuditLogPort, never()).findRecent(anyInt());
        verify(loadAuditLogPort, never()).findByActor(
                anyLong(), anyInt());
    }
}
