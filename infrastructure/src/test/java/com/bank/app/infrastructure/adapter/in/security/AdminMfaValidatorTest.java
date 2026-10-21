package com.bank.app.infrastructure.adapter.in.security;

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
class AdminMfaValidatorTest {

    @Mock
    private LoadUserPort loadUserPort;

    @Test
    void acceptsEnrolledAdmin() {
        when(loadUserPort.findMfaEnabledById(42L)).thenReturn(Optional.of(true));

        assertTrue(new AdminMfaValidator(loadUserPort).isMfaEnabled(42L));
    }

    @Test
    void rejectsPasswordOnlyAdmin() {
        when(loadUserPort.findMfaEnabledById(42L)).thenReturn(Optional.of(false));

        assertFalse(new AdminMfaValidator(loadUserPort).isMfaEnabled(42L));
    }

    @Test
    void rejectsMissingUserFailClosed() {
        when(loadUserPort.findMfaEnabledById(42L)).thenReturn(Optional.empty());

        assertFalse(new AdminMfaValidator(loadUserPort).isMfaEnabled(42L));
    }
}
