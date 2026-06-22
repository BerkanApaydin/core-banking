package com.bank.app.user.infrastructure.config;

import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.out.PasswordEncoderPort;
import com.bank.app.user.application.port.out.SaveUserPort;
import com.bank.app.user.application.usecase.LoginUserUseCaseImpl;
import com.bank.app.user.application.usecase.RegisterUserUseCaseImpl;
import com.bank.app.common.security.port.out.JwtPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class UserDomainConfig {

    @Bean
    @Transactional
    public RegisterUserUseCase registerUserUseCase(LoadUserPort loadUserPort, SaveUserPort saveUserPort, PasswordEncoderPort passwordEncoderPort) {
        return new RegisterUserUseCaseImpl(loadUserPort, saveUserPort, passwordEncoderPort);
    }

    @Bean
    public LoginUserUseCase loginUserUseCase(AuthenticationPort authenticationPort, JwtPort jwtPort, LoadUserPort loadUserPort) {
        return new LoginUserUseCaseImpl(authenticationPort, jwtPort, loadUserPort);
    }
}
