package com.bank.app.user.infrastructure.decorator;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserPort;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.usecase.LoginUserUseCase;
import com.bank.app.user.exception.UserNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginUserUseCaseDecorator implements LoginUserPort {

    private final LoginUserUseCase delegate;

    public LoginUserUseCaseDecorator(AuthenticationPort authenticationPort, JwtPort jwtPort, LoadUserPort loadUserPort) {
        this.delegate = new LoginUserUseCase(authenticationPort, jwtPort, loadUserPort);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse execute(AuthRequest request) {
        try {
            return delegate.execute(request);
        } catch (UserNotFoundException ex) {
            throw new BadCredentialsException("Kullanıcı adı veya şifre hatalı", ex);
        }
    }
}
