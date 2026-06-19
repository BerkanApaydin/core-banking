package com.bank.app.audit.application;

import com.bank.app.audit.application.port.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private SaveAuditLogPort saveAuditLogPort;

    private AuditService auditService;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(saveAuditLogPort);
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void shouldLogWithExplicitUsername() {
        auditService.log("user123", AuditAction.ACCOUNT_CREATED, "Account created details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("user123", saved.getUsername());
        assertEquals(AuditAction.ACCOUNT_CREATED, saved.getAction());
        assertEquals("Account created details", saved.getDetails());
    }

    @Test
    void shouldReturnSystemUserWhenAuthIsNull() {
        SecurityContextHolder.getContext().setAuthentication(null);

        auditService.log(AuditAction.TRANSFER_EXECUTED, "Transfer executed");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
        assertEquals(AuditAction.TRANSFER_EXECUTED, saved.getAction());
    }

    @Test
    void shouldReturnSystemUserWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditService.log(AuditAction.TRANSFER_CANCELLED, "Transfer cancelled");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldReturnSystemUserWhenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditService.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldReturnSystemUserWhenAnonymousUsername() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditService.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getUsername());
    }

    @Test
    void shouldLogWithCurrentUsernameWhenAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("jane.doe");
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditService.log(AuditAction.ACCOUNT_CREATED, "Details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(saveAuditLogPort).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("jane.doe", saved.getUsername());
    }
}
