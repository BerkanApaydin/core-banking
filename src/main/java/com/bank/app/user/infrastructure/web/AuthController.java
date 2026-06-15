package com.bank.app.user.infrastructure.web;

import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.usecase.LoginUserUseCase;
import com.bank.app.user.application.usecase.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase, LoginUserUseCase loginUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
    }

    @PostMapping("/register")
    @Operation(summary = "Yeni kullanıcı kaydı oluşturur", description = "Kullanıcı adı ve şifre ile sisteme yeni üye olunmasını sağlar.")
    public ResponseEntity<Void> register(@Valid @RequestBody AuthRequest request) {
        registerUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Kullanıcı girişi yapar", description = "Geçerli kullanıcı bilgileriyle giriş yaparak JWT token üretilmesini sağlar.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(loginUserUseCase.execute(request));
    }
}
