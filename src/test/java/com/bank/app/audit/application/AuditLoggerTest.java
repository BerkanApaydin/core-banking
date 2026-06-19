package com.bank.app.audit.application;

import com.bank.app.audit.application.port.out.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;
    @Mock
    private SecurityContextPort securityContextPort;

    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLogger(saveAuditLogPort, securityContextPort);
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
    void shouldReturnSystemUserWhenAuthIsNull() {
        when(securityContextPort.getCurrentUsername()).thenReturn(Optional.empty());

        auditLogger.log(AuditAction.TRANSFER_EXECUTED, "Transfer executed");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, saved.getAction());
    }

    @Test
    void shouldReturnSystemUserWhenNotAuthenticated() {
        when(securityContextPort.getCurrentUsername()).thenReturn(Optional.empty());

        auditLogger.log(AuditAction.TRANSFER_CANCELLED, "Transfer cancelled");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldReturnSystemUserWhenAnonymousToken() {
        when(securityContextPort.getCurrentUsername()).thenReturn(Optional.empty());

        auditLogger.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldReturnSystemUserWhenAnonymousUsername() {
        when(securityContextPort.getCurrentUsername()).thenReturn(Optional.empty());

        auditLogger.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldLogWithCurrentUsernameWhenAuthenticated() {
        when(securityContextPort.getCurrentUsername()).thenReturn(Optional.of("jane.doe"));

        auditLogger.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("jane.doe", saved.getUsername());
    }
}
