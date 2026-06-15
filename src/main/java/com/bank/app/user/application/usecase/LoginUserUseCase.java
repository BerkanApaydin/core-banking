package com.bank.app.user.application.usecase;

import com.bank.app.common.security.JwtService;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.domain.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class LoginUserUseCase {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoadUserPort loadUserPort;

    public LoginUserUseCase(AuthenticationManager authenticationManager, JwtService jwtService, LoadUserPort loadUserPort) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loadUserPort = loadUserPort;
    }

    public AuthResponse execute(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = loadUserPort.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
