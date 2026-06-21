package com.bank.app.user.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
    @NotBlank(message = "{validation.username.required}") String username,
    @NotBlank(message = "{validation.password.required}") String password,
    @Email(message = "{validation.email.invalid}") String email,
    String phone
) {
    public AuthRequest(String username, String password) {
        this(username, password, null, null);
    }
}
