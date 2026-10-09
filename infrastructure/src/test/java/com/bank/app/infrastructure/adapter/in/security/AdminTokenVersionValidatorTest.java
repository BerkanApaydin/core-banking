package com.bank.app.infrastructure.adapter.in.security;

import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTokenVersionValidatorTest {

    @Mock
    private JwtPort jwtPort;

    @Mock
    private LoadUserPort loadUserPort;

    @Test
    void acceptsMatchingGeneration() {
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(3L);
        when(loadUserPort.findTokenVersionById(42L)).thenReturn(Optional.of(3L));

        assertTrue(new AdminTokenVersionValidator(jwtPort, loadUserPort)
                .hasCurrentTokenVersion("jwt", 42L));
    }

    @Test
    void rejectsStaleGenerationAfterDemotion() {
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(2L);
        when(loadUserPort.findTokenVersionById(42L)).thenReturn(Optional.of(3L));

        assertFalse(new AdminTokenVersionValidator(jwtPort, loadUserPort)
                .hasCurrentTokenVersion("jwt", 42L));
    }

    @Test
    void rejectsDeletedUserFailClosed() {
        when(jwtPort.extractTokenVersion("jwt")).thenReturn(0L);
        when(loadUserPort.findTokenVersionById(42L)).thenReturn(Optional.empty());

        assertFalse(new AdminTokenVersionValidator(jwtPort, loadUserPort)
                .hasCurrentTokenVersion("jwt", 42L));
    }

    @Test
    void matchesAdminPaths() {
        assertTrue(FilterRequestDecisions.isAdminRequest("/api/v1/admin/accounts/1/suspend"));
        assertTrue(FilterRequestDecisions.isAdminRequest("/actuator/loggers"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/transfers"));
        assertFalse(FilterRequestDecisions.isAdminRequest("/api/v1/administrator"));
    }
}
