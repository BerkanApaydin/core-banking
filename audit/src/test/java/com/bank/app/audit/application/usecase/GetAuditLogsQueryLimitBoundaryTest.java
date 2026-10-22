package com.bank.app.audit.application.usecase;

import com.bank.app.audit.application.port.out.LoadAuditLogPort;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The clamp is the only thing standing between an admin request and an
 * unbounded audit-table scan, so its boundaries are pinned exactly: the
 * limit handed to the load port must stay inside [1, maxQueryLimit].
 */
@ExtendWith(MockitoExtension.class)
class GetAuditLogsQueryLimitBoundaryTest {

    @Mock
    private LoadAuditLogPort loadAuditLogPort;

    @Mock
    private UserContextService userContextService;

    private GetAuditLogsQueryImpl query;

    @BeforeEach
    void setUp() {
        query = new GetAuditLogsQueryImpl(loadAuditLogPort, userContextService, 500);
        when(userContextService.getCurrentUserId()).thenReturn(Optional.of(99L));
        when(userContextService.hasRole("ROLE_ADMIN")).thenReturn(true);
    }

    @ParameterizedTest(name = "limit {0} is clamped to {1}")
    @CsvSource({
            "0, 1",
            "1, 1",
            "500, 500",
            "501, 500",
            "2147483647, 500",
            "-1, 1"
    })
    void shouldClampTheLimitIntoTheLegalRange(int requestedLimit, int expectedLimit) {
        when(loadAuditLogPort.findRecent(expectedLimit)).thenReturn(List.of());

        query.execute(requestedLimit);

        verify(loadAuditLogPort).findRecent(expectedLimit);
    }

    @Test
    void shouldApplyTheSameClampToTheActorFilteredQuery() {
        when(loadAuditLogPort.findByActor(7L, 500)).thenReturn(List.of());

        query.execute(7L, 10_000);

        verify(loadAuditLogPort).findByActor(7L, 500);
        verify(loadAuditLogPort, never()).findRecent(anyInt());
    }

    @Test
    void shouldReturnEveryActorWhenTheActorFilterIsAbsent() {
        // A missing actorUserId is "all actors", not "no rows": the admin view
        // must not silently go empty because the filter was left out.
        when(loadAuditLogPort.findRecent(500)).thenReturn(List.of());

        query.execute(null, 10_000);

        verify(loadAuditLogPort).findRecent(500);
        verify(loadAuditLogPort, never()).findByActor(anyLong(), anyInt());
    }
}
