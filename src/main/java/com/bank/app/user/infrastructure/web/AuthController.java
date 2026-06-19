package com.bank.app.user.infrastructure.web;

import com.bank.app.user.exception.TooManyFailedLoginAttemptsException;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserPort;
import com.bank.app.user.application.port.in.RegisterUserPort;
import com.bank.app.user.infrastructure.web.FailedLoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth API", description = "Kullanıcı kayıt ve kimlik doğrulama API'si")
public class AuthController {

    private final RegisterUserPort registerUserPort;
    private final LoginUserPort loginUserPort;
    private final FailedLoginAttemptService failedLoginAttemptService;

    public AuthController(RegisterUserPort registerUserPort, LoginUserPort loginUserPort,
                          FailedLoginAttemptService failedLoginAttemptService) {
        this.registerUserPort = registerUserPort;
        this.loginUserPort = loginUserPort;
        this.failedLoginAttemptService = failedLoginAttemptService;
    }

    @PostMapping("/register")
    @Operation(summary = "Yeni kullanıcı kaydı oluşturur", description = "Kullanıcı adı ve şifre ile sisteme yeni üye olunmasını sağlar.")
    public ResponseEntity<Void> register(@Valid @RequestBody AuthRequest request) {
        registerUserPort.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Kullanıcı girişi yapar", description = "Geçerli kullanıcı bilgileriyle giriş yaparak JWT token üretilmesini sağlar.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);

        if (failedLoginAttemptService.isBlocked(ip)) {
            throw new TooManyFailedLoginAttemptsException(
                    "Çok fazla başarısız giriş denemesi. Lütfen " + failedLoginAttemptService.getWindowMinutes()
                            + " dakika sonra tekrar deneyin.");
        }

        try {
            AuthResponse response = loginUserPort.execute(request);
            failedLoginAttemptService.reset(ip);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            failedLoginAttemptService.recordFailure(ip);
            throw e;
        }
    }

    private String resolveClientIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return httpRequest.getRemoteAddr();
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex != -1 ? ip.substring(0, commaIndex).trim() : ip.trim();
    }
}
