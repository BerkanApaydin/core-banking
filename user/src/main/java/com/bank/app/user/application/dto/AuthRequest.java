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
    }

    public AuthRequest(String username, String password) {
        this(username, password, null, null);
    }
}
