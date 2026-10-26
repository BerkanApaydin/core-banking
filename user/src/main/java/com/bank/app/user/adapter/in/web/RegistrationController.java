package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.user.adapter.in.web.dto.PasswordResetConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.PasswordResetRequestWebRequest;
import com.bank.app.user.adapter.in.web.dto.RegisterWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registration and password-reset endpoints (anonymous, no bearer token). */
@RestController
@ApiVersion("v1")
@RequestMapping("/auth")
@Tag(name = "Auth API", description = "User registration and password reset API")
public class RegistrationController {

    private final RegisterUserUseCase registerUserUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    public RegistrationController(RegisterUserUseCase registerUserUseCase,
            RequestPasswordResetUseCase requestPasswordResetUseCase,
            ConfirmPasswordResetUseCase confirmPasswordResetUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.confirmPasswordResetUseCase = confirmPasswordResetUseCase;
    }

    @PostMapping("/register")
    @Operation(summary = "Creates a new user registration", description = "Registers a new user with username and password.")
    @Idempotent(publicEndpoint = true)
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterWebRequest webRequest) {
        AuthRequest request = new AuthRequest(
                webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        registerUserUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/password/reset/request")
    @Operation(summary = "Requests a password reset", description = "Mints a single-use reset token and sends it out-of-band. Always returns 202, whether or not the account exists (no enumeration).")
    public ResponseEntity<Void> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequestWebRequest webRequest) {
        requestPasswordResetUseCase.execute(webRequest.username());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset/confirm")
    @Operation(summary = "Confirms a password reset", description = "Consumes the single-use token and sets the new password; outstanding sessions are retired.")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmWebRequest webRequest) {
        confirmPasswordResetUseCase.execute(webRequest.token(), webRequest.newPassword());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
