package com.bank.app.user.application.dto;

import java.util.Objects;

public record AuthRequest(
    String username,
    String password,
    String email,
    String phone
) {
    public AuthRequest {
        Objects.requireNonNull(username, "Username must not be null");
        Objects.requireNonNull(password, "Password must not be null");
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
        this(username, password, null, null);
    }
}
