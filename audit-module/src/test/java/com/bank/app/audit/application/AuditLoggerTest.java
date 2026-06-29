package com.bank.app.audit.application;

import com.bank.app.audit.application.port.in.AuditLoggerUseCase;
import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.application.usecase.AuditLoggerUseCaseImpl;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.application.service.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;
    @Mock
    private UserContextService userContextService;

    private AuditLoggerUseCase auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLoggerUseCaseImpl(saveAuditLogPort, userContextService);
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
        when(userContextService.getCurrentUsername()).thenReturn(Optional.empty());

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
        when(userContextService.getCurrentUsername()).thenReturn(Optional.of("jane.doe"));

        auditLogger.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("jane.doe", saved.getUsername());
    }
}
