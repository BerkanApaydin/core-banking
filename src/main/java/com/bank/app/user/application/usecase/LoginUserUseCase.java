package com.bank.app.user.application.usecase;

import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.LoginUserPort;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.domain.User;
import org.springframework.stereotype.Service;

@Service
public class LoginUserUseCase implements LoginUserPort {

    private final AuthenticationPort authenticationPort;
    private final JwtPort jwtPort;
    private final LoadUserPort loadUserPort;

    public LoginUserUseCase(AuthenticationPort authenticationPort, JwtPort jwtPort, LoadUserPort loadUserPort) {
        this.authenticationPort = authenticationPort;
        this.jwtPort = jwtPort;
        this.loadUserPort = loadUserPort;
    }

    public AuthResponse execute(AuthRequest request) {
        authenticationPort.authenticate(request.username(), request.password());

        User user = loadUserPort.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));

        String token = jwtPort.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
