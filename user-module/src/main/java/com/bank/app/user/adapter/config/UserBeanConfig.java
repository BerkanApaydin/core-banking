package com.bank.app.user.adapter.config;

import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.usecase.LoginUserUseCaseImpl;
import com.bank.app.user.application.usecase.RegisterUserUseCaseImpl;
import com.bank.app.user.domain.PasswordPolicy;
import com.bank.app.common.application.port.out.security.JwtPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PasswordPolicyProperties.class)
public class UserBeanConfig {

    @Bean
    public PasswordPolicy passwordPolicy(PasswordPolicyProperties props) {
        return props.toDomain();
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort,
                                                    PasswordEncoderPort passwordEncoderPort,
                                                    PasswordPolicy passwordPolicy) {
        return new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort, passwordPolicy);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(AuthenticationPort authenticationPort, JwtPort jwtPort,
                                             LoadUserPort loadUserPort, LoginAttemptPort loginAttemptPort) {
        return new LoginUserUseCaseImpl(authenticationPort, jwtPort, loadUserPort, loginAttemptPort);
    }
}
