package com.bank.app.user.adapter.in.config;

import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.port.out.JwtPort;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class UserBeanConfigTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoderPort passwordEncoderPort;
    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private DomainEventPublisherService domainEventPublisherService;

    @Test
    void shouldCreateRegisterUserUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        RegisterUserUseCase useCase = config.registerUserUseCase(loadUserPort, saveUserPort, passwordEncoderPort, PasswordPolicy.DEFAULT, domainEventPublisherService);
        assertNotNull(useCase);
    }

    @Test
    void shouldCreateLoginUserUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        LoginUserUseCase useCase = config.loginUserUseCase(authenticationPort, jwtPort, loadUserPort, loginAttemptPort);
        assertNotNull(useCase);
    }
}
