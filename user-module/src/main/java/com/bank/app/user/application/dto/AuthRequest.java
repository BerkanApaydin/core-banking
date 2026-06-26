package com.bank.app.user.application.dto;

import java.util.Objects;

public record AuthRequest(
    String username,
    String password,
    String email,
    String phone
) {
    public AuthRequest {
        Objects.requireNonNull(username, "Kullanıcı adı null olamaz");
        Objects.requireNonNull(password, "Şifre null olamaz");
    }

    public AuthRequest(String username, String password) {
        this(username, password, null, null);
    }
}
