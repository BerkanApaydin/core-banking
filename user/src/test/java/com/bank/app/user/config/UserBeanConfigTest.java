package com.bank.app.user.config;

import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.adapter.out.security.AesGcmMfaEncryptor;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class UserBeanConfigTest {

    @Mock private LoadUserPort loadUserPort;
    @Mock private SaveUserPort saveUserPort;
    @Mock private PasswordEncoderPort passwordEncoderPort;
    @Mock private AuthenticationPort authenticationPort;
    @Mock private JwtPort jwtPort;
    @Mock private LoginAttemptPort loginAttemptPort;
    @Mock private RefreshTokenPort refreshTokenPort;
    @Mock private TokenBlacklistPort tokenBlacklistPort;
    @Mock private DomainEventPublisherService domainEventPublisherService;
    @Mock private ClockProviderPort clockProvider;
    @Mock private AuditEventPort auditEventPort;
    @Mock private IdGeneratorPort idGenerator;
    @Mock private MfaSecretPort mfaSecretPort;
    @Mock private MfaAttemptPort mfaAttemptPort;

    @Test
    void shouldCreateRegisterUserUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        RegisterUserUseCase useCase = config.registerUserUseCase(loadUserPort, saveUserPort, passwordEncoderPort, PasswordPolicy.DEFAULT, domainEventPublisherService, clockProvider);
        assertNotNull(useCase);
    }

    @Test
    void shouldCreateLoginUserUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        MfaCodeVerificationService mfaCodeVerification = new MfaCodeVerificationService(
                mfaSecretPort, AesGcmMfaEncryptor.ephemeral(), new TotpPolicy(30, 6, 1),
                mfaAttemptPort, clockProvider);
        LoginUserUseCase useCase = config.loginUserUseCase(authenticationPort, jwtPort,
                loginAttemptPort, refreshTokenPort, passwordEncoderPort, idGenerator, clockProvider, auditEventPort,
                mfaCodeVerification);
        assertNotNull(useCase);
    }

    @Test
    void shouldCreateRefreshSessionUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        assertNotNull(config.refreshSessionUseCase(jwtPort, refreshTokenPort, loadUserPort, clockProvider, auditEventPort));
    }

    @Test
    void shouldCreateMfaStatusUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        assertNotNull(config.mfaStatusUseCase(loadUserPort));
    }

    @Test
    void shouldCreateLogoutUseCaseBean() {
        UserBeanConfig config = new UserBeanConfig();
        assertNotNull(config.logoutUseCase(tokenBlacklistPort, jwtPort, refreshTokenPort,
                clockProvider, auditEventPort));
    }

    @Test
    void shouldExposePasswordPolicyBean() {
        UserBeanConfig config = new UserBeanConfig();
        var policy = config.passwordPolicy(
                new PasswordPolicyProperties(12, true, true, true));
        assertNotNull(policy);
        assertThat(policy.minLength()).isEqualTo(12);
    }
}
