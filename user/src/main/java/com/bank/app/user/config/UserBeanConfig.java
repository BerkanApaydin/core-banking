package com.bank.app.user.config;

import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.MfaAttemptPort;
import com.bank.app.user.application.port.out.MfaSecretEncryptorPort;
import com.bank.app.user.application.port.out.MfaSecretPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.PasswordResetNotificationPort;
import com.bank.app.user.application.port.out.PasswordResetTokenPort;
import com.bank.app.user.application.port.out.RefreshTokenPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.usecase.ConfirmMfaUseCaseImpl;
import com.bank.app.user.application.usecase.ConfirmPasswordResetUseCaseImpl;
import com.bank.app.user.application.usecase.DisableMfaUseCaseImpl;
import com.bank.app.user.application.usecase.EnableMfaUseCaseImpl;
import com.bank.app.user.application.usecase.MfaStatusUseCaseImpl;
import com.bank.app.user.application.usecase.RequestPasswordResetUseCaseImpl;
import com.bank.app.user.application.service.MfaCodeVerificationService;
import com.bank.app.user.adapter.out.security.AesGcmMfaEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.bank.app.user.application.usecase.LoginUserUseCaseImpl;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.application.usecase.RefreshSessionUseCaseImpl;
import com.bank.app.user.application.usecase.RegisterUserUseCaseImpl;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.user.domain.TotpPolicy;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.user.application.port.out.JwtPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.usecase.LogoutUseCaseImpl;
import com.bank.app.user.application.port.out.TokenBlacklistPort;
import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.IdGeneratorPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        PasswordPolicyProperties.class,
        BrowserSessionProperties.class,
        SessionTokenLifetimeProperties.class,
        MfaProperties.class
})
public class UserBeanConfig {

    private static final Logger log = LoggerFactory.getLogger(UserBeanConfig.class);

    @Bean
    public PasswordPolicy passwordPolicy(PasswordPolicyProperties props) {
        return props.toDomain();
    }

    @Bean
    public TotpPolicy totpPolicy(MfaProperties props) {
        return props.toTotpPolicy();
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
                                             PasswordEncoderPort passwordEncoderPort, IdGeneratorPort idGenerator,
                                             ClockProviderPort clockProvider, AuditEventPort auditEventPort,
                                             MfaCodeVerificationService mfaCodeVerification) {
        return new LoginUserUseCaseImpl(authenticationPort, jwtPort, loginAttemptPort, refreshTokenPort,
                passwordEncoderPort, idGenerator, clockProvider, auditEventPort,
                mfaCodeVerification);
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

    /**
     * TOTP-secret encryptor. A blank {@code MFA_ENCRYPTION_KEY} falls back to
     * an ephemeral in-memory key (dev/test convenience — secrets die with the
     * JVM, loudly logged). Production refuses to boot without a real key
     * (see {@code ApplicationStartupValidator}).
     */
    @Bean
    public MfaSecretEncryptorPort mfaSecretEncryptor(MfaProperties properties) {
        if (properties.encryptionKeyBase64() == null || properties.encryptionKeyBase64().isBlank()) {
            log.warn("MFA_ENCRYPTION_KEY is not set: using an ephemeral in-memory key. "
                    + "Enrolled TOTP secrets will be unreadable after restart; "
                    + "set MFA_ENCRYPTION_KEY (Base64, 32 bytes) for any durable environment.");
            return AesGcmMfaEncryptor.ephemeral();
        }
        return AesGcmMfaEncryptor.fromBase64OrEphemeral(
                properties.encryptionKeyBase64(), properties.previousEncryptionKeysBase64());
    }

    @Bean
    public MfaStatusUseCase mfaStatusUseCase(LoadUserPort loadUserPort) {
        return new MfaStatusUseCaseImpl(loadUserPort);
    }

    /**
     * Single place where a TOTP code is verified and consumed: brute-force
     * bound, replay protection and the uniform failure contract. Login,
     * enrollment confirmation and MFA removal all go through it.
     */
    @Bean
    public MfaCodeVerificationService mfaCodeVerificationService(MfaSecretPort mfaSecretPort,
                                                               MfaSecretEncryptorPort mfaEncryptor,
                                                               TotpPolicy totpPolicy,
                                                               MfaAttemptPort mfaAttemptPort,
                                                               ClockProviderPort clockProvider) {
        return new MfaCodeVerificationService(mfaSecretPort, mfaEncryptor, totpPolicy,
                mfaAttemptPort, clockProvider);
    }

    @Bean
    public EnableMfaUseCase enableMfaUseCase(LoadUserPort loadUserPort,
                                            AuthenticationPort authenticationPort,
                                            MfaSecretPort mfaSecretPort,
                                            MfaSecretEncryptorPort mfaEncryptor, MfaProperties mfaProperties,
                                            TotpPolicy totpPolicy,
                                            ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        // URI digits/period stay in sync with the verification policy.
        return EnableMfaUseCaseImpl.withPolicy(loadUserPort, authenticationPort, mfaSecretPort,
                mfaEncryptor, mfaProperties.issuer(), totpPolicy, clockProvider, auditEventPort);
    }

    @Bean
    public ConfirmMfaUseCase confirmMfaUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                              AuthenticationPort authenticationPort,
                                              RefreshTokenPort refreshTokenPort,
                                              MfaSecretPort mfaSecretPort,
                                              MfaCodeVerificationService mfaCodeVerification,
                                              ClockProviderPort clockProvider,
                                              AuditEventPort auditEventPort) {
        return new ConfirmMfaUseCaseImpl(loadUserPort, saveUserPort, authenticationPort,
                refreshTokenPort, mfaSecretPort, mfaCodeVerification, clockProvider, auditEventPort);
    }

    @Bean
    public DisableMfaUseCase disableMfaUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                               AuthenticationPort authenticationPort,
                                               RefreshTokenPort refreshTokenPort,
                                               MfaSecretPort mfaSecretPort,
                                               MfaCodeVerificationService mfaCodeVerification,
                                               ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new DisableMfaUseCaseImpl(loadUserPort, saveUserPort, authenticationPort,
                refreshTokenPort, mfaSecretPort, mfaCodeVerification, clockProvider, auditEventPort);
    }

    @Bean
    public RequestPasswordResetUseCase requestPasswordResetUseCase(LoadUserPort loadUserPort,
            PasswordResetTokenPort resetTokenPort, PasswordResetNotificationPort notificationPort,
            MfaProperties mfaProperties, ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new RequestPasswordResetUseCaseImpl(loadUserPort, resetTokenPort, notificationPort,
                mfaProperties.resetTokenValidityMinutes(), clockProvider, auditEventPort);
    }

    @Bean
    public ConfirmPasswordResetUseCase confirmPasswordResetUseCase(PasswordResetTokenPort resetTokenPort,
            LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordEncoderPort passwordEncoderPort,
            PasswordPolicy passwordPolicy, ClockProviderPort clockProvider, AuditEventPort auditEventPort) {
        return new ConfirmPasswordResetUseCaseImpl(resetTokenPort, loadUserPort, saveUserPort,
                passwordEncoderPort, passwordPolicy, clockProvider, auditEventPort);
    }
}
