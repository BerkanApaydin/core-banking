package com.bank.app.user.config;

import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.usecase.LoginUserUseCaseImpl;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.usecase.RefreshSessionUseCaseImpl;
import com.bank.app.user.application.usecase.RegisterUserUseCaseImpl;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.usecase.LogoutUseCaseImpl;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        PasswordPolicyProperties.class,
        BrowserSessionProperties.class,
        SessionTokenLifetimeProperties.class
})
public class UserBeanConfig {

    @Bean
    public PasswordPolicy passwordPolicy(PasswordPolicyProperties props) {
        return props.toDomain();
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                                    PasswordEncoderPort passwordEncoderPort,
                                                    PasswordPolicy passwordPolicy,
                                                    DomainEventPublisherService domainEventPublisherService,
                                                    ClockProviderPort clockProvider) {
        return new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort, passwordPolicy, domainEventPublisherService, clockProvider);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(AuthenticationPort authenticationPort, JwtPort jwtPort,
                                             LoginAttemptPort loginAttemptPort, RefreshTokenPort refreshTokenPort,
                                             ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new LoginUserUseCaseImpl(authenticationPort, jwtPort, loginAttemptPort, refreshTokenPort,
                clockProvider, auditEventPort);
    }

    @Bean
    public RefreshSessionUseCase refreshSessionUseCase(JwtPort jwtPort, RefreshTokenPort refreshTokenPort,
                                                       LoadUserPort loadUserPort,
                                                       ClockProviderPort clockProvider,
                                                       AuditEventPort auditEventPort) {
        return new RefreshSessionUseCaseImpl(jwtPort, refreshTokenPort, loadUserPort, clockProvider, auditEventPort);
    }

    @Bean
    public LogoutUseCase logoutUseCase(TokenBlacklistPort tokenBlacklistPort, JwtPort jwtPort,
                                       RefreshTokenPort refreshTokenPort,
                                       ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new LogoutUseCaseImpl(tokenBlacklistPort, jwtPort, refreshTokenPort,
                clockProvider, auditEventPort);
    }
}
