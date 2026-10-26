package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.adapter.in.idempotency.Idempotent;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.application.port.out.ClientIpResolverPort;
import com.bank.app.user.application.port.in.LogoutUseCase;
import com.bank.app.user.application.port.in.RefreshSessionUseCase;
import com.bank.app.user.adapter.in.web.dto.AuthWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaDisableWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaEnableWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaStatusResponse;
import com.bank.app.user.adapter.in.web.dto.PasswordResetConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.PasswordResetRequestWebRequest;
import com.bank.app.user.adapter.in.web.dto.RefreshTokenWebRequest;
import com.bank.app.user.adapter.in.web.dto.RegisterWebRequest;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.dto.AuthResponse;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.in.ConfirmPasswordResetUseCase;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import com.bank.app.user.application.port.in.LoginUserUseCase;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.in.RequestPasswordResetUseCase;
import com.bank.app.user.adapter.in.web.support.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final MfaStatusUseCase mfaStatusUseCase;
    private final EnableMfaUseCase enableMfaUseCase;
    private final ConfirmMfaUseCase confirmMfaUseCase;
    private final DisableMfaUseCase disableMfaUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    private final UserContextService userContextService;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase,
                          ClientIpResolverPort clientIpResolver,
                          LogoutUseCase logoutUseCase,
                          RefreshSessionUseCase refreshSessionUseCase,
                          MfaStatusUseCase mfaStatusUseCase,
                          EnableMfaUseCase enableMfaUseCase,
                          ConfirmMfaUseCase confirmMfaUseCase,
                          DisableMfaUseCase disableMfaUseCase,
                          RequestPasswordResetUseCase requestPasswordResetUseCase,
                          ConfirmPasswordResetUseCase confirmPasswordResetUseCase,
                          UserContextService userContextService) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.clientIpResolver = clientIpResolver;
        this.logoutUseCase = logoutUseCase;
        this.refreshSessionUseCase = refreshSessionUseCase;
        this.mfaStatusUseCase = mfaStatusUseCase;
        this.enableMfaUseCase = enableMfaUseCase;
        this.confirmMfaUseCase = confirmMfaUseCase;
        this.disableMfaUseCase = disableMfaUseCase;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.confirmPasswordResetUseCase = confirmPasswordResetUseCase;
        this.userContextService = userContextService;
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
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody(required = false) RefreshTokenWebRequest body,
            HttpServletRequest httpRequest) {
        String refreshToken = body == null ? null : body.refreshToken();
        boolean hasAuthHeader = authHeader != null && !authHeader.isBlank();
        if (hasAuthHeader && !isUsableBearerToken(authHeader)) {
            // A foreign scheme ("Basic abc") or an empty bearer value is not a
            // credential. Answering 204 claimed a logout that never happened,
            // leaving the session usable while the client believed otherwise.
            return ProblemResponses.unauthorized(
                    "Authentication failed.", httpRequest.getRequestURI());
        }
        if (!hasAuthHeader && (refreshToken == null || refreshToken.isBlank())) {
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

    @GetMapping("/mfa/status")
    @Operation(summary = "Reads MFA enrollment status", description = "Returns whether MFA is enabled for the authenticated user. Drives the UI security section.")
    public ResponseEntity<?> mfaStatus(HttpServletRequest httpRequest) {
        Long userId = currentUserId();
        if (userId == null) {
            return ProblemResponses.unauthorized("You must be logged in to perform this action.",
                    httpRequest.getRequestURI());
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MfaStatusResponse(mfaStatusUseCase.isEnabled(userId)));
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Starts MFA enrollment", description = "Generates a TOTP secret and returns authenticator enrollment material. Requires authentication; MFA stays off until confirmed.")
    public ResponseEntity<?> enableMfa(@Valid @RequestBody MfaEnableWebRequest webRequest,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId();
        if (userId == null) {
            return ProblemResponses.unauthorized("You must be logged in to perform this action.",
                    httpRequest.getRequestURI());
        }
        MfaEnrollment enrollment = enableMfaUseCase.execute(userId, webRequest.password());
        // Enrollment material contains the TOTP secret (password-equivalent):
        // never allow heuristic caching by browsers or proxies.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(enrollment);
    }

    @PostMapping("/mfa/confirm")
    @Operation(summary = "Confirms MFA enrollment", description = "Verifies a TOTP code against the pending secret and enables MFA for subsequent logins.")
    public ResponseEntity<?> confirmMfa(@Valid @RequestBody MfaConfirmWebRequest webRequest,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId();
        if (userId == null) {
            return ProblemResponses.unauthorized("You must be logged in to perform this action.",
                    httpRequest.getRequestURI());
        }
        confirmMfaUseCase.execute(userId, webRequest.password(), webRequest.code());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disables MFA", description = "Re-authenticates with the password, verifies a current TOTP code, then removes the second factor and deletes the secret.")
    public ResponseEntity<?> disableMfa(@Valid @RequestBody MfaDisableWebRequest webRequest,
            HttpServletRequest httpRequest) {
        Long userId = currentUserId();
        if (userId == null) {
            return ProblemResponses.unauthorized("You must be logged in to perform this action.",
                    httpRequest.getRequestURI());
        }
        disableMfaUseCase.execute(userId, webRequest.password(), webRequest.code());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
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

    /**
     * Authenticated-only endpoints (mfa/*): an absent principal means "not
     * logged in" (401 body, same pattern as BrowserAuthController.session) —
     * never a domain exception (controllers must not depend on
     * {@code ..domain.exception..} per LayeringArchitectureTest).
     */
    private Long currentUserId() {
        return userContextService.getCurrentUserId().orElse(null);
    }
}
