package com.bank.app.user.application.usecase;

import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.domain.User;
import com.bank.app.user.application.exception.UserNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private final AuthenticationPort authenticationPort;
    private final JwtPort jwtPort;
    private final LoadUserPort loadUserPort;

    public LoginUserUseCaseImpl(AuthenticationPort authenticationPort, JwtPort jwtPort, LoadUserPort loadUserPort) {
        this.authenticationPort = authenticationPort;
        this.jwtPort = jwtPort;
        this.loadUserPort = loadUserPort;
    }

    @Override
    public AuthResponse execute(AuthRequest request) {
        authenticationPort.authenticate(request.username(), request.password());

        User user = loadUserPort.findByUsername(request.username())
                .orElseThrow(() -> new UserNotFoundException("Kullanıcı bulunamadı"));

        String token = jwtPort.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
