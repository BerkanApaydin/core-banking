package com.bank.app.user.application.usecase;

import com.bank.app.common.application.UseCase;
import com.bank.app.common.application.port.out.security.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.domain.User;
import com.bank.app.user.application.exception.UserNotFoundException;
import com.bank.app.user.domain.exception.AuthenticationFailedException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@UseCase
public class LoginUserUseCaseImpl implements LoginUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoginUserUseCaseImpl.class);

    private final AuthenticationPort authenticationPort;
    private final JwtPort jwtPort;
    private final LoadUserPort loadUserPort;
    private final LoginAttemptPort loginAttemptPort;

    public LoginUserUseCaseImpl(AuthenticationPort authenticationPort, JwtPort jwtPort,
                                LoadUserPort loadUserPort, LoginAttemptPort loginAttemptPort) {
        this.authenticationPort = authenticationPort;
        this.jwtPort = jwtPort;
        this.loadUserPort = loadUserPort;
        this.loginAttemptPort = loginAttemptPort;
    }

    @Override
    public AuthResponse execute(AuthRequest request) {
        return execute(request, null);
    }

    @Override
    public AuthResponse execute(AuthRequest request, String clientIp) {
        String username = request.username();

        if (clientIp != null && loginAttemptPort.isIpBlocked(clientIp)) {
            throw new TooManyFailedLoginAttemptsException(
                    "Çok fazla başarısız giriş denemesi. Lütfen "
                    + loginAttemptPort.getWindowMinutes() + " dakika sonra tekrar deneyin.");
        }

        if (username != null && loginAttemptPort.isUsernameBlocked(username)) {
            throw new TooManyFailedLoginAttemptsException(
                    "Bu kullanıcı adı için çok fazla başarısız giriş denemesi. Lütfen "
                    + loginAttemptPort.getWindowMinutes() + " dakika sonra tekrar deneyin.");
        }

        try {
            authenticationPort.authenticate(username, request.password());
            User user = loadUserPort.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
            String token = jwtPort.generateToken(user.getId().value(), user.getUsername(), user.getRole().name());
            if (clientIp != null) loginAttemptPort.reset(clientIp);
            loginAttemptPort.resetByUsername(username);
            log.info("User logged in: username={}, userId={}", username, user.getId().value());
            return new AuthResponse(token, user.getId().value(), user.getUsername());
        } catch (AuthenticationFailedException | UserNotFoundException e) {
            log.warn("Failed login attempt: username={}, clientIp={}", username, clientIp);
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            throw e;
        } catch (Exception e) {
            log.warn("Unexpected error during login: username={}, clientIp={}", username, clientIp, e);
            if (clientIp != null) loginAttemptPort.recordFailure(clientIp, username);
            throw new AuthenticationFailedException("Geçersiz kullanıcı adı veya şifre.", e);
        }
    }
}
