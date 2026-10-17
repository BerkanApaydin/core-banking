package com.bank.app.user.application.usecase;

import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import com.bank.app.user.domain.UserTestFactory;
import com.bank.app.user.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaStatusUseCase")
class MfaStatusUseCaseTest {

    @Mock private LoadUserPort loadUserPort;

    @Test
    @DisplayName("should reflect the enrollment flag")
    void shouldReflectEnrollmentFlag() {
        MfaStatusUseCaseImpl status = new MfaStatusUseCaseImpl(loadUserPort);
        User plain = UserTestFactory.newUser("alice");
        User enrolled = UserTestFactory.newUser("bob");
        enrolled.enableMfa();
        when(loadUserPort.findById(7L)).thenReturn(Optional.of(plain));
        when(loadUserPort.findById(8L)).thenReturn(Optional.of(enrolled));

        assertThat(status.isEnabled(7L)).isFalse();
        assertThat(status.isEnabled(8L)).isTrue();
    }

    @Test
    @DisplayName("should fail for unknown users")
    void shouldFailForUnknownUsers() {
        MfaStatusUseCaseImpl status = new MfaStatusUseCaseImpl(loadUserPort);
        when(loadUserPort.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> status.isEnabled(9L)).isInstanceOf(UserNotFoundException.class);
    }
}
