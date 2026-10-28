package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.application.exception.AuthenticationRequiredException;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.dto.RefreshTokenWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.common.adapter.in.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bearer-token authentication endpoints. Registration, password reset and
 * MFA live in dedicated controllers so each web adapter owns one capability
 * and a small dependency set (the previous single controller had twelve
 * collaborators and ten endpoints).
 */
@RestController
@ApiVersion("v1")
@RequestMapping("/auth")
@Tag(name = "Auth API", description = "User authentication API")
public class AuthController {

    private final LoginUserUseCase loginUserUseCase;
    private final ClientIpResolver clientIpResolver;
    private final LogoutUseCase logoutUseCase;
    private final RefreshSessionUseCase refreshSessionUseCase;

    public AuthController(LoginUserUseCase loginUserUseCase,
                          ClientIpResolver clientIpResolver,
                          LogoutUseCase logoutUseCase,
                          RefreshSessionUseCase refreshSessionUseCase) {
        this.loginUserUseCase = loginUserUseCase;
        this.clientIpResolver = clientIpResolver;
        this.logoutUseCase = logoutUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticates a user", description = "Validates credentials and generates a JWT token. MFA-enrolled users must also supply the TOTP code (totpCode).")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthWebRequest webRequest, HttpServletRequest httpRequest) {
        AuthRequest request = new AuthRequest(
                webRequest.username(), webRequest.password(),
                webRequest.email(), webRequest.phone(), webRequest.totpCode());
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
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenWebRequest webRequest) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(refreshSessionUseCase.execute(webRequest.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logs out the user", description = "Invalidates the current JWT token and, when supplied, the refresh-token session. "
            + "Authorization header is optional so an expired access token does not block refresh-token revocation; "
            + "at least one credential (header or refresh body) is required.")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody(required = false) RefreshTokenWebRequest body) {
        String refreshToken = body == null ? null : body.refreshToken();
        boolean hasAuthHeader = authHeader != null && !authHeader.isBlank();
        if (hasAuthHeader && !isUsableBearerToken(authHeader)) {
            // A foreign scheme ("Basic abc") or an empty bearer value is not a
            // credential. Answering 204 claimed a logout that never happened,
            // leaving the session usable while the client believed otherwise.
            throw new AuthenticationRequiredException();
        }
        if (!hasAuthHeader && (refreshToken == null || refreshToken.isBlank())) {
            // Anonymous logout is not a no-op: same 401 as the previous
            // required-header path produced via MissingRequestHeaderException.
            throw new AuthenticationRequiredException();
        }
        logoutUseCase.execute(authHeader, refreshToken);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Only a non-empty Bearer credential may reach the logout use case; the
     * scheme check is case-insensitive per RFC 7235.
     */
    private static boolean isUsableBearerToken(String authHeader) {
        if (!authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        return !authHeader.substring(BEARER_PREFIX.length()).isBlank();
    }
}
