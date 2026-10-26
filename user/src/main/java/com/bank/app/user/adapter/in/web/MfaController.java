package com.bank.app.user.adapter.in.web;

import com.bank.app.common.adapter.in.api.ApiVersion;
import com.bank.app.common.application.exception.AuthenticationRequiredException;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.user.adapter.in.web.dto.MfaConfirmWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaDisableWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaEnableWebRequest;
import com.bank.app.user.adapter.in.web.dto.MfaStatusResponse;
import com.bank.app.user.application.dto.MfaEnrollment;
import com.bank.app.user.application.port.in.ConfirmMfaUseCase;
import com.bank.app.user.application.port.in.DisableMfaUseCase;
import com.bank.app.user.application.port.in.EnableMfaUseCase;
import com.bank.app.user.application.port.in.MfaStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated-only MFA enrollment and lifecycle endpoints. */
@RestController
@ApiVersion("v1")
@RequestMapping("/auth/mfa")
@Tag(name = "MFA API", description = "Multi-factor authentication enrollment API")
public class MfaController {

    private final MfaStatusUseCase mfaStatusUseCase;
    private final EnableMfaUseCase enableMfaUseCase;
    private final ConfirmMfaUseCase confirmMfaUseCase;
    private final DisableMfaUseCase disableMfaUseCase;
    private final UserContextService userContextService;

    public MfaController(MfaStatusUseCase mfaStatusUseCase,
            EnableMfaUseCase enableMfaUseCase,
            ConfirmMfaUseCase confirmMfaUseCase,
            DisableMfaUseCase disableMfaUseCase,
            UserContextService userContextService) {
        this.mfaStatusUseCase = mfaStatusUseCase;
        this.enableMfaUseCase = enableMfaUseCase;
        this.confirmMfaUseCase = confirmMfaUseCase;
        this.disableMfaUseCase = disableMfaUseCase;
        this.userContextService = userContextService;
    }

    @GetMapping("/status")
    @Operation(summary = "Reads MFA enrollment status", description = "Returns whether MFA is enabled for the authenticated user. Drives the UI security section.")
    public ResponseEntity<MfaStatusResponse> mfaStatus() {
        Long userId = userContextService.getCurrentUserId().orElseThrow(AuthenticationRequiredException::new);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MfaStatusResponse(mfaStatusUseCase.isEnabled(userId)));
    }

    @PostMapping("/enable")
    @Operation(summary = "Starts MFA enrollment", description = "Generates a TOTP secret and returns authenticator enrollment material. Requires authentication; MFA stays off until confirmed.")
    public ResponseEntity<MfaEnrollment> enableMfa(@Valid @RequestBody MfaEnableWebRequest webRequest) {
        Long userId = userContextService.getCurrentUserId().orElseThrow(AuthenticationRequiredException::new);
        MfaEnrollment enrollment = enableMfaUseCase.execute(userId, webRequest.password());
        // Enrollment material contains the TOTP secret (password-equivalent):
        // never allow heuristic caching by browsers or proxies.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(enrollment);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirms MFA enrollment", description = "Verifies a TOTP code against the pending secret and enables MFA for subsequent logins.")
    public ResponseEntity<Void> confirmMfa(@Valid @RequestBody MfaConfirmWebRequest webRequest) {
        Long userId = userContextService.getCurrentUserId().orElseThrow(AuthenticationRequiredException::new);
        confirmMfaUseCase.execute(userId, webRequest.password(), webRequest.code());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/disable")
    @Operation(summary = "Disables MFA", description = "Re-authenticates with the password, verifies a current TOTP code, then removes the second factor and deletes the secret.")
    public ResponseEntity<Void> disableMfa(@Valid @RequestBody MfaDisableWebRequest webRequest) {
        Long userId = userContextService.getCurrentUserId().orElseThrow(AuthenticationRequiredException::new);
        disableMfaUseCase.execute(userId, webRequest.password(), webRequest.code());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

