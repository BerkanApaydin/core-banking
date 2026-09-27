package com.bank.app.user.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration-only web contract. Split from {@link AuthWebRequest} (login):
 * login must stay lenient so existing credentials always reach the
 * authentication backend, while registration enforces the full policy shape
 * up front (minimum length mirrors {@code PasswordPolicyProperties}, phone
 * mirrors the {@code PhoneNumber} domain pattern).
 */
public record RegisterWebRequest(
    @NotBlank(message = "{validation.username.required}")
    @Size(max = 255, message = "{validation.username.tooLong}") String username,
    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.tooShort}")
    @Size(max = 72, message = "{validation.password.tooLong}") String password,
    @Email(message = "{validation.email.invalid}")
    @Size(max = 254, message = "{validation.email.tooLong}") String email,
    @Pattern(regexp = "^\\+?[\\d\\s.-]{6,20}$", message = "{validation.phone.invalid}") String phone
) {
}
