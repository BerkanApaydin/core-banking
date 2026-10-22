package com.bank.app.user.application.port.out;

import com.bank.app.user.domain.User;
import com.bank.app.user.domain.UserTestFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("port default methods")
class PortDefaultsTest {

    @Test
    void loadUserPortDefaultsDelegateToThePrimaryLookup() {
        LoadUserPort port = mock(LoadUserPort.class, CALLS_REAL_METHODS);
        User user = UserTestFactory.newUser("alice");
        user.enableMfa();
        when(port.findByUsername("alice")).thenReturn(Optional.of(user));
        when(port.findById(7L)).thenReturn(Optional.of(user));

        assertThat(port.findByUsernameForUpdate("alice")).contains(user);
        assertThat(port.findTokenVersionById(7L)).contains(user.getTokenVersion());
        assertThat(port.findMfaEnabledById(7L)).contains(true);
    }

    @Test
    void loadUserPortIdLookupDefaultsToEmpty() {
        LoadUserPort port = mock(LoadUserPort.class, CALLS_REAL_METHODS);

        assertThat(port.findById(7L)).isEmpty();
        assertThat(port.findTokenVersionById(7L)).isEmpty();
        assertThat(port.findMfaEnabledById(7L)).isEmpty();
    }

    @Test
    void loginAttemptPortDelegatesWithoutAnIp() {
        LoginAttemptPort port = mock(LoginAttemptPort.class, CALLS_REAL_METHODS);
        when(port.isUsernameBlocked("alice", null)).thenReturn(true);
        when(port.isUsernameBlocked("bob", null)).thenReturn(false);

        assertThat(port.isUsernameBlocked("alice")).isTrue();
        assertThat(port.isUsernameBlocked("bob")).isFalse();
        verify(port).isUsernameBlocked("alice", null);
        verify(port).isUsernameBlocked("bob", null);
    }

    @Test
    void mfaSecretPortReencryptFallsBackToSaveSecret() {
        MfaSecretPort port = mock(MfaSecretPort.class, CALLS_REAL_METHODS);

        port.reencryptSecret(7L, "cipher");

        verify(port).saveSecret(7L, "cipher");
    }

    @Test
    void mfaSecretEncryptorWithoutARingTreatsEveryKeyAsCurrent() {
        MfaSecretEncryptorPort port = mock(MfaSecretEncryptorPort.class, CALLS_REAL_METHODS);

        assertThat(port.isCurrentKey("v1:key:cipher")).isTrue();
        assertThat(port.isCurrentKey(null)).isTrue();
    }
}
