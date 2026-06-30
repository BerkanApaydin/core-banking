package com.bank.app.user.application.usecase;


import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.common.application.port.out.TokenBlacklistPort;
import com.bank.app.common.application.port.out.JwtPort;

public class LogoutUseCaseImpl implements LogoutUseCase {

    private final TokenBlacklistPort tokenBlacklistPort;
    private final JwtPort jwtPort;

    public LogoutUseCaseImpl(TokenBlacklistPort tokenBlacklistPort, JwtPort jwtPort) {
        this.tokenBlacklistPort = tokenBlacklistPort;
        this.jwtPort = jwtPort;
    }

    @Override
    public void execute(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long expirationMs = jwtPort.getExpirationMs();
            tokenBlacklistPort.blacklist(token, expirationMs);
        }
    }
}
