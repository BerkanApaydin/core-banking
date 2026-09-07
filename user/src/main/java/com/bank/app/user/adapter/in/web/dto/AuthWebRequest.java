package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthWebRequest(
    @NotBlank(message = "{validation.username.required}")
    @Size(max = 255, message = "{validation.username.tooLong}") String username,
    @NotBlank(message = "{validation.password.required}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password,
    @Email(message = "{validation.email.invalid}")
    @Size(max = 254, message = "{validation.email.tooLong}") String email,
    String phone
) {
    public AuthWebRequest(String username, String password) {
        this(username, password, null, null);
    }
}
