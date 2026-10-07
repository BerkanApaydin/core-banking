package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.dto.RefreshTokenRequest;
import com.bank.app.user.adapter.in.web.dto.RegisterWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.adapter.in.web.support.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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
    private final RefreshSessionUseCase refreshSessionUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase,
                          ClientIpResolverPort clientIpResolver,
                          LogoutUseCase logoutUseCase,
                          RefreshSessionUseCase refreshSessionUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.clientIpResolver = clientIpResolver;
        this.logoutUseCase = logoutUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
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

    @PostMapping("/login")
    @Operation(summary = "Authenticates a user", description = "Validates credentials and generates a JWT token.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthWebRequest webRequest, HttpServletRequest httpRequest) {
        AuthRequest request = new AuthRequest(
                webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone());
        String ip = clientIpResolver.resolveClientIp(
                httpRequest.getHeader("X-Forwarded-For"), httpRequest.getRemoteAddr());
        AuthResponse response = loginUserUseCase.execute(request, ip);
        // Bearer tokens in JSON must never be heuristically cached by browsers
        // or proxies (the browser-cookie flow already sends no-store).
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotates a refresh token", description = "Exchanges a valid refresh token for a fresh access + refresh pair. Re-presenting a rotated token revokes its whole family.")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest webRequest) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(refreshSessionUseCase.execute(webRequest.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logs out the user", description = "Invalidates the current JWT token and, when supplied, the refresh-token session. "
            + "Authorization header is optional so an expired access token does not block refresh-token revocation; "
            + "at least one credential (header or refresh body) is required.")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest httpRequest) {
        String refreshToken = body == null ? null : body.refreshToken();
        if ((authHeader == null || authHeader.isBlank())
                && (refreshToken == null || refreshToken.isBlank())) {
            // Same 401 AUTHENTICATION_FAILED the required-header path produced
            // via MissingRequestHeaderException: anonymous logout is not a no-op.
            return ProblemResponses.unauthorized(
                    "Authentication failed.", httpRequest.getRequestURI());
        }
        logoutUseCase.execute(authHeader, refreshToken);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
