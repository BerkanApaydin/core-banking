package com.bank.app.user.application.dto;

import java.util.Objects;

public record AuthRequest(
    String username,
    String password,
    String email,
    String phone,
    // Audit (MFA): second factor for MFA-enrolled users. Nullable — most
    // callers (registration, tests, seeders) have no code; login passes the
    // web-layer field through. Blank is treated as absent (missing code fails
    // with the generic authentication message when MFA is on).
    String totpCode
) {
    public AuthRequest {
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(password, "Password must not be null");
        // R-U16: canonicalize the username at the application boundary so
        // trailing/leading whitespace cannot create a distinct login-attempt
        // key from the stored/authenticated value. The password is never
        // stripped: whitespace is part of the secret.
        username = username.strip();
        // Defense in depth behind web-layer @NotBlank: direct callers
        // (seeders, tests, future adapters) must not slip blank credentials
        // past the application boundary. No jakarta import here by design
        // (application.dto must stay validation-framework-free per ArchUnit).
        if (username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
    }

    public AuthRequest(String username, String password) {
        this(username, password, null, null, null);
    }

    public AuthRequest(String username, String password, String email, String phone) {
        this(username, password, email, phone, null);
    }
}
