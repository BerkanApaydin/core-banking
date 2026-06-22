package com.bank.app.user.application.usecase;

import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.out.AuthenticationPort;
import com.bank.app.user.application.port.out.LoginAttemptPort;
import com.bank.app.user.domain.User;
import com.bank.app.user.application.exception.UserNotFoundException;
import com.bank.app.user.domain.exception.TooManyFailedLoginAttemptsException;
import org.springframework.security.core.AuthenticationException;
public class LoginUserUseCaseImpl implements LoginUserUseCase {

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
                    .orElseThrow(() -> new UserNotFoundException("Kullanıcı bulunamadı"));

            String token = jwtPort.generateToken(user.getId(), user.getUsername(), user.getRole());

            if (clientIp != null) {
                loginAttemptPort.reset(clientIp);
            }
            loginAttemptPort.resetByUsername(username);

            return new AuthResponse(token, user.getId(), user.getUsername());
        } catch (AuthenticationException e) {
            if (clientIp != null) {
                loginAttemptPort.recordFailure(clientIp, username);
            }
            throw e;
        }
    }
}
