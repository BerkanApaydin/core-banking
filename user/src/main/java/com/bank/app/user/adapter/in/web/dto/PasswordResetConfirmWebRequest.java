package com.bank.app.user.adapter.in.web.dto;

import com.bank.app.user.adapter.in.web.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password-reset completion: single-use token plus the new password. */
public record PasswordResetConfirmWebRequest(
    @NotBlank(message = "{validation.token.required}") String token,
    @NotBlank(message = "{validation.password.required}")
    @ValidPassword
    @Size(max = 72, message = "{validation.password.tooLong}") String newPassword
) {
}
