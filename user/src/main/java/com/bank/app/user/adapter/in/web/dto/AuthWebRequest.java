package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AuthWebRequest(
    @NotBlank(message = "{validation.username.required}") String username,
    @NotBlank(message = "{validation.password.required}") String password,
    @Email(message = "{validation.email.invalid}") String email,
    String phone
) {
    public AuthWebRequest(String username, String password) {
        this(username, password, null, null);
    }
}
