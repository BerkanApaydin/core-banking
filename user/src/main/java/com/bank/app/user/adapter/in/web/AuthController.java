package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@ApiVersion("v1")
@RequestMapping("/auth")
@Tag(name = "Auth API", description = "User registration and authentication API")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final ClientIpResolverPort clientIpResolver;
    private final LogoutUseCase logoutUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase,
                          ClientIpResolverPort clientIpResolver,
                          LogoutUseCase logoutUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.clientIpResolver = clientIpResolver;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/register")
    @Operation(summary = "Creates a new user registration", description = "Registers a new user with username and password.")
    @Idempotent(publicEndpoint = true)
    public ResponseEntity<Void> register(@Valid @RequestBody AuthWebRequest webRequest) {
        AuthRequest request = new AuthRequest(
                webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        registerUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticates a user", description = "Validates credentials and generates a JWT token.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthWebRequest webRequest, HttpServletRequest httpRequest) {
        AuthRequest request = new AuthRequest(
                webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        String ip = clientIpResolver.resolveClientIp(httpRequest);
        AuthResponse response = loginUserUseCase.execute(request, ip);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logs out the user", description = "Invalidates the current JWT token.")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        logoutUseCase.execute(authHeader);
        return ResponseEntity.noContent().build();
    }
}
