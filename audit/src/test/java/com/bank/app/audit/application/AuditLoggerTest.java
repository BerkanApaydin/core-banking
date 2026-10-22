package com.bank.app.audit.application;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.application.usecase.AuditLoggerUseCaseImpl;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;
    @Mock
    private UserContextService userContextService;
    @Mock
    private ClockProviderPort clockProvider;

    private AuditLoggerUseCase auditLogger;

    @BeforeEach
    void setUp() {
        when(clockProvider.clock()).thenReturn(
                java.time.Clock.fixed(java.time.Instant.parse("2026-06-22T10:00:00Z"),
                        java.time.ZoneOffset.UTC));
        auditLogger = new AuditLoggerUseCaseImpl(saveAuditLogPort, userContextService, clockProvider);
    }

    @Test
    void shouldLogWithExplicitUsername() {
        auditLogger.log("user123", AuditAction.ACCOUNT_CREATED, "Account created details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("user123", saved.getUsername());
        assertEquals(AuditAction.ACCOUNT_CREATED, saved.getAction());
        assertEquals("Account created details", saved.getDetails());
    }

    @Test
    void shouldUseSystemUserWhenNoAuthentication() {
        when(userContextService.getCurrentUsernameOrSystem()).thenReturn("system");

        auditLogger.log(AuditAction.TRANSFER_EXECUTED, "Transfer executed");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, saved.getAction());
        assertEquals("Transfer executed", saved.getDetails());

        verifyNoMoreInteractions(saveAuditLogPort);
    }

    @Test
    void shouldLogWithCurrentUsernameWhenAuthenticated() {
        when(userContextService.getCurrentUsernameOrSystem()).thenReturn("jane.doe");

        auditLogger.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("jane.doe", saved.getUsername());
    }

    @Test
    void shouldPropagatePersistenceFailureInsteadOfClaimingTheAuditWasRecorded() {
        doThrow(new IllegalStateException("audit store unavailable"))
                .when(saveAuditLogPort).save(any(AuditLog.class));

        assertThrows(IllegalStateException.class,
                () -> auditLogger.log("user123", AuditAction.ACCOUNT_CREATED, "details"));

        verify(saveAuditLogPort).save(any(AuditLog.class));
    }

    @Test
    void shouldRejectANullUsernameWithoutWritingAnUnattributedRow() {
        // This use case runs REQUIRES_NEW inside the business transaction: an
        // NPE escaping it aborts the caller's transaction, so a caller bug
        // would refuse an otherwise valid transfer.
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> auditLogger.log(null, AuditAction.ACCOUNT_CREATED, "details"));

        assertEquals("Username must not be null", failure.getMessage());
        verify(saveAuditLogPort, never()).save(any(AuditLog.class));
    }

    @Test
    void shouldRejectNullDetailsWithoutWritingAnUnattributedRow() {
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> auditLogger.log("user123", AuditAction.ACCOUNT_CREATED, null));

        assertEquals("Details must not be null", failure.getMessage());
        verify(saveAuditLogPort, never()).save(any(AuditLog.class));
    }

    @Test
    void shouldRejectANullActionWithoutWritingAnUnattributedRow() {
        NullPointerException failure = assertThrows(NullPointerException.class,
                () -> auditLogger.log("user123", null, "details"));

        assertEquals("Action must not be null", failure.getMessage());
        verify(saveAuditLogPort, never()).save(any(AuditLog.class));
    }

    @Test
    void shouldRecordTheActorIdentityFromTheSecurityContext() {
        when(userContextService.getCurrentUserId()).thenReturn(java.util.Optional.of(42L));

        auditLogger.log("user123", AuditAction.ACCOUNT_CREATED, "Account created details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        assertEquals(42L, captor.getValue().getActorUserId());
    }

    @Test
    void shouldLeaveTheActorIdentityNullForAnonymousAndBackgroundWrites() {
        when(userContextService.getCurrentUserId()).thenReturn(java.util.Optional.empty());

        auditLogger.log("system", AuditAction.ACCOUNT_CREATED, "Account created details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        assertNull(captor.getValue().getActorUserId());
    }
}
